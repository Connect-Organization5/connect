package connect.order_service.domain.service;

import connect.order_service.domain.client.clientInterface.*;
import connect.order_service.domain.client.dto.request.DeliveryCreateRequest;
import connect.order_service.domain.client.dto.request.DeliveryReceiverRequest;
import connect.order_service.domain.client.dto.response.*;
import connect.order_service.domain.dto.CreateOrderRequest;
import connect.order_service.domain.dto.OrderRequester;
import connect.order_service.domain.dto.OrderResponse;
import connect.order_service.domain.dto.UpdateOrderRequest;
import connect.order_service.domain.entity.Order;
import connect.order_service.domain.entity.OrderStatus;
import connect.order_service.domain.repository.OrderRepository;
import connect.order_service.global.dto.PagedResponse;
import connect.order_service.global.exception.BaseException;
import connect.order_service.global.exception.ErrorCode;
import connect.order_service.global.slack.OrderCreatedNotification;
import connect.order_service.global.slack.OrderNotificationService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final CompanyClient companyClient;
    private final HubClient hubClient;
    private final DeliveryClient deliveryClient;
    private final OrderNotificationService orderNotificationService;

    private static final String MASTER_ROLE = "MASTER";
    private static final String MODIFIABLE_DELIVERY_STATUS = "HUB_WAITING";
    private static final String CANCELED_DELIVERY_STATUS = "CANCELED";

    public OrderResponse createOrder(UUID userId, String userAffiliationType, UUID userAffiliationId, CreateOrderRequest request) {
        OrderRequester requester = findRequester(userAffiliationType, userAffiliationId);
        validateProduct(request.getSupplierCompanyId(), request.getProductId());
        CompanyInfo supplier = findSupplier(request.getSupplierCompanyId());

        Order order = orderRepository.save(Order.builder()
                .requesterCompanyId(requester.companyId())
                .supplierCompanyId(request.getSupplierCompanyId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .requestMessage(request.getRequestMessage())
                .receiver(request.getReceiver())
                .build());

        StockResult stockResult;
        try {
            stockResult = hubClient.decreaseStock(
                    order.getId(), supplier.hubId(), request.getProductId(), request.getQuantity());
        } catch (Exception e) {
            log.error("재고 차감 실패 - orderId={}", order.getId(), e);
            restoreStock(order, supplier.hubId(), "재고 차감 실패");
            fail(order);
            throw new BaseException(ErrorCode.STOCK_DECREASE_FAILED);
        }
        if (stockResult != StockResult.SUCCESS) {
            fail(order);
            throw new BaseException(toStockErrorCode(stockResult, ErrorCode.OUT_OF_STOCK));
        }

        UUID deliveryId;
        try {
            deliveryId = deliveryClient.createDelivery(new DeliveryCreateRequest(
                    order.getId(),
                    userId,
                    supplier.hubId(),           // 출발 허브
                    requester.arrivalHubId(),   // 도착 허브
                    requester.address(),        // 배송지
                    order.getReceiver()));
        } catch (Exception e) {
            log.error("배송 생성 실패 - orderId={}", order.getId(), e);
            compensateDeliveryAndStock(order, supplier.hubId(), "배송 생성 실패");
            fail(order);
            throw new BaseException(ErrorCode.DELIVERY_CREATE_FAILED);
        }

        try {
            order.complete(deliveryId);
            orderRepository.save(order);
        } catch (Exception e) {
            log.error("주문 저장 실패 - orderId={}", order.getId(), e);
            compensateDeliveryAndStock(order, supplier.hubId(), "주문 저장 실패");
            try {
                fail(order);
            } catch (Exception ex) {
                log.error("주문 저장 실패 후 실패 상태 저장 실패 - orderId={}", order.getId(), ex);
            }
            throw new BaseException(ErrorCode.ORDER_SAVE_FAILED);
        }

        orderNotificationService.notifyOrderCreated(OrderCreatedNotification.of(order, userId, supplier.userId()));

        return OrderResponse.from(order);
    }

    public OrderResponse searchOrder(UUID userId, String role, UUID orderId) {
        Order order = findActiveOrder(orderId);

        // 관리자가 아니면 본인 주문만 조회 가능
        if (!isMaster(role) && !userId.equals(order.getCreatedBy())) {
            throw new BaseException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return OrderResponse.from(order);
    }

    public PagedResponse<OrderResponse> searchPageOrders(UUID userId, String role, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // 관리자는 전체, 일반 사용자는 본인 주문만 조회 (실패된 주문 포함)
        Page<Order> orders = isMaster(role)
                ? orderRepository.findAllByDeletedAtIsNull(pageable)
                : orderRepository.findAllByCreatedByAndDeletedAtIsNull(userId, pageable);

        return PagedResponse.from(orders.map(OrderResponse::from));
    }

    public OrderResponse searchReceivedOrder(UUID userId, String role, UUID orderId) {
        Order order = findActiveOrder(orderId);

        // 관리자가 아니면 상품 담당자만 가능
        if (!isMaster(role)){
            CompanyInfo supplier = findSupplier(order.getSupplierCompanyId());
            if(!userId.equals(supplier.userId())) {
                throw new BaseException(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED);
            }
        }

        return OrderResponse.from(order);
    }

    public PagedResponse<OrderResponse> searchReceivedPageOrders(UUID userId, String role, String userAffiliationType, UUID userAffiliationId, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // 관리자는 전체, 상품 담당자는 본인 업체가 공급 업체인 주문만 조회
        if (isMaster(role)) {
            return PagedResponse.from(orderRepository.findAllByDeletedAtIsNull(pageable)
                    .map(OrderResponse::from));
        }

        if (!"COMPANY".equals(userAffiliationType)) {
            throw new BaseException(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED);
        }
        CompanyInfo company = findAffiliationCompany(userAffiliationId);
        if (!userId.equals(company.userId())) {
            throw new BaseException(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED);
        }

        Page<Order> orders = orderRepository
                .findAllBySupplierCompanyIdAndStatusNotAndDeletedAtIsNull(company.id(), OrderStatus.FAILED, pageable);

        return PagedResponse.from(orders.map(OrderResponse::from));
    }

    public OrderResponse updateOrder(UUID userId, String role, UUID orderId, UpdateOrderRequest request) {
        Order order = findActiveOrder(orderId);

        // 관리자가 아니면 본인만 가능
        if (!isMaster(role) && !userId.equals(order.getCreatedBy())) {
            throw new BaseException(ErrorCode.ORDER_MODIFY_DENIED);
        }

        if (!order.isModifiable()) {
            throw new BaseException(ErrorCode.ORDER_NOT_MODIFIABLE);
        }
        validateDeliveryReady(order, userId, role);

        CompanyInfo supplier = findSupplier(order.getSupplierCompanyId());

        // 1. 재고 조정
        adjustStock(order.getId(), order.getStockAdjustSeq(), supplier, order.getProductId(), order.getQuantity(), request.getQuantity());

        // 2. 배송 수정, 실패하면 재고 조정을 되돌림
        try {
            deliveryClient.changeReceiver(order.getDeliveryId(), userId, role, new DeliveryReceiverRequest(
                    request.getReceiver()));
        } catch (Exception e) {
            log.error("배송 수정 실패 - orderId={}", order.getId(), e);
            try {
                adjustStock(order.getId(), order.getStockAdjustSeq(), supplier, order.getProductId(), request.getQuantity(), order.getQuantity());
                order.nextStockAdjust();
                orderRepository.save(order);
            } catch (Exception ex) {
                log.error("배송 수정 실패 후 재고 보상 실패 - orderId={}", order.getId(), ex);
            }
            throw new BaseException(ErrorCode.DELIVERY_UPDATE_FAILED);
        }

        // 3. 주문 수정
        order.update(request.getQuantity(), request.getRequestMessage(), request.getReceiver());
        return OrderResponse.from(orderRepository.save(order));
    }

    public void deleteOrder(UUID userId, String role, UUID orderId) {
        Order order = findActiveOrder(orderId);

        if (!isMaster(role) && !userId.equals(order.getCreatedBy())) {
                throw new BaseException(ErrorCode.ORDER_DELETE_DENIED);
        }

        // 재고 부족 등으로 이미 취소된 주문은 재고와 배송 처리 없이 삭제만
        if (order.isModifiable()) {
            validateDeliveryCancelable(order, userId, role);
            try{
                deliveryClient.cancelDelivery(order.getId());
            } catch (Exception ex) {
                log.error("주문 배송 삭제 실패 - orderId={}", order.getId(), ex);
                throw new BaseException(ErrorCode.DELIVERY_DELETE_FAILED);

            }
            // 배송은 이미 취소됐으므로 재고 복구가 실패해도 주문은 취소 처리하고 기록을 남김
            try {
                CompanyInfo supplier = companyClient.getSupplyCompany(order.getSupplierCompanyId());
                hubClient.restoreStock(order.getId(), supplier.hubId(), order.getProductId());
            } catch (Exception e) {
                log.error("주문 취소 후 재고 복구 실패 - orderId={}", order.getId(), e);
            }
        }

        order.delete(userId);
        orderRepository.save(order);
    }

    //---------------------------------------------------------
    private void fail(Order order) {
        order.fail();
        orderRepository.save(order);
    }

    private void compensateDeliveryAndStock(Order order, UUID hubId, String reason) {
        try {
            deliveryClient.cancelDelivery(order.getId());
        } catch (Exception e) {
            log.error("{} 후 배송 취소 보상 실패 - orderId={}", reason, order.getId(), e);
        }
        restoreStock(order, hubId, reason);
    }

    private void restoreStock(Order order, UUID hubId, String reason) {
        try {
            hubClient.restoreStock(order.getId(), hubId, order.getProductId());
        } catch (Exception e) {
            log.error("{} 후 재고 보상 실패 - orderId={}", reason, order.getId(), e);
        }
    }

    private boolean isMaster(String role) {
        return MASTER_ROLE.equalsIgnoreCase(role);
    }

    private Order findActiveOrder(UUID orderId) {
        return orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new BaseException(ErrorCode.ORDER_NOT_FOUND));
    }

    private OrderRequester findRequester(String affiliationType, UUID affiliationId) {
        return switch (affiliationType) {
            case "COMPANY" -> OrderRequester.from(findAffiliationCompany(affiliationId));
            case "HUB" -> {
                try {
                    yield OrderRequester.from(hubClient.getHub(affiliationId));
                } catch (FeignException.NotFound e) {
                    throw new BaseException(ErrorCode.AFFILIATION_NOT_FOUND);
                }
            }
            default -> throw new BaseException(ErrorCode.AFFILIATION_NOT_FOUND);
        };
    }

    private CompanyInfo findAffiliationCompany(UUID companyId) {
        try {
            return companyClient.getSupplyCompany(companyId);
        } catch (FeignException.NotFound e) {
            throw new BaseException(ErrorCode.AFFILIATION_NOT_FOUND);
        }
    }

    private CompanyInfo findSupplier(UUID supplierCompanyId) {
        try {
            return companyClient.getSupplyCompany(supplierCompanyId);
        } catch (FeignException.NotFound e) {
            throw new BaseException(ErrorCode.SUPPLIER_COMPANY_NOT_FOUND);
        }
    }

    private void validateProduct(UUID supplierCompanyId, UUID productId) {
        try {
            companyClient.validateProduct(supplierCompanyId, productId);
        } catch (FeignException.NotFound e) {
            throw new BaseException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    private void validateDeliveryReady(Order order, UUID userId, String role) {
        if (!MODIFIABLE_DELIVERY_STATUS.equals(fetchDelivery(order, userId, role).deliveryStatus())) {
            throw new BaseException(ErrorCode.ORDER_DELIVERY_STARTED);
        }
    }

    private void validateDeliveryCancelable(Order order, UUID userId, String role) {
        String status = fetchDelivery(order, userId, role).deliveryStatus();
        if (!MODIFIABLE_DELIVERY_STATUS.equals(status) && !CANCELED_DELIVERY_STATUS.equals(status)) {
            throw new BaseException(ErrorCode.ORDER_DELIVERY_STARTED);
        }
    }

    private DeliveryInfo fetchDelivery(Order order, UUID userId, String role) {
        try {
            return deliveryClient.getDelivery(order.getDeliveryId(), userId, role);
        } catch (FeignException.NotFound e) {
            throw new BaseException(ErrorCode.DELIVERY_NOT_FOUND);
        } catch (Exception e) {
            log.error("배송 조회 실패 - orderId={}", order.getId(), e);
            throw new BaseException(ErrorCode.DELIVERY_FETCH_FAILED);
        }
    }

    private void adjustStock(UUID orderId, int seq, CompanyInfo supplier, UUID productId, int quantity, int updateQuantity) {
        int diff = updateQuantity - quantity;
        if (diff == 0) {
            return;
        }

        UUID adjustmentId = UUID.nameUUIDFromBytes(
                (orderId + ":" + seq + ":" + quantity + "->" + updateQuantity).getBytes(StandardCharsets.UTF_8));

        StockResult result;
        try {
            result = hubClient.adjustStock(adjustmentId, orderId, supplier.hubId(), productId, diff);
        } catch (Exception e) {
            log.error("재고 조정 실패 - orderId={}", orderId, e);
            throw new BaseException(ErrorCode.STOCK_ADJUST_FAILED);
        }
        if (result != StockResult.SUCCESS) {
            throw new BaseException(toStockErrorCode(result, ErrorCode.OUT_OF_STOCK_FOR_UPDATE));
        }
    }

    // 재고 거절 사유를 응답 에러로 변환, 재고 부족은 생성·수정마다 메시지가 달라 호출부에서 받음
    private ErrorCode toStockErrorCode(StockResult result, ErrorCode outOfStock) {
        return switch (result) {
            case DISCONTINUED -> ErrorCode.PRODUCT_DISCONTINUED;
            case NOT_READY -> ErrorCode.PRODUCT_NOT_READY;
            default -> outOfStock;
        };
    }
}
