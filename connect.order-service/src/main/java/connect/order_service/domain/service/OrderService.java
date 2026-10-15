package connect.order_service.domain.service;

import connect.order_service.domain.client.clientInterface.*;
import connect.order_service.domain.client.dto.*;
import connect.order_service.domain.dto.CreateOrderRequest;
import connect.order_service.domain.dto.OrderResponse;
import connect.order_service.domain.dto.UpdateOrderRequest;
import connect.order_service.domain.entity.Order;
import connect.order_service.domain.entity.OrderStatus;
import connect.order_service.domain.repository.OrderRepository;
import connect.order_service.global.dto.PagedResponse;
import connect.order_service.global.exception.BaseException;
import connect.order_service.global.exception.ErrorCode;
import connect.order_service.global.slack.SlackWebhookSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final CompanyClient companyClient;
    private final HubClient hubClient;
    private final DeliveryClient deliveryClient;
    private final SlackWebhookSender slackWebhookSender;

    private static final String MASTER_ROLE = "MASTER";
    private static final String MODIFIABLE_DELIVERY_STATUS = "READY";

    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        UserInfo orderer = userClient.getUser(userId);
        CompanyInfo requester = companyClient.getCompany(userId);
        ProductInfo product = companyClient.getProduct(request.getProductId());
        CompanyInfo supplier = companyClient.getSupplyCompany(product.supplierCompanyId());

        Order order = orderRepository.save(Order.builder()
                .requesterCompanyId(requester.companyId())
                .supplierCompanyId(product.supplierCompanyId())
                .productId(product.productId())
                .quantity(request.getQuantity())
                .requestMessage(request.getRequestMessage())
                .receiver(request.getReceiver())
                .build());

        boolean decreased;
        try {
            decreased = hubClient.decreaseStock(
                    supplier.hubId(), product.productId(), request.getQuantity(), order.getId());
        } catch (Exception e) {
            log.error("재고 차감 실패 - orderId={}", order.getId(), e);
            fail(order);
            throw new BaseException(ErrorCode.STOCK_DECREASE_FAILED);
        }
        if (!decreased) {
            fail(order);
            throw new BaseException(ErrorCode.OUT_OF_STOCK);
        }

        UUID deliveryId;
        try {
            deliveryId = deliveryClient.createDelivery(new DeliveryCreateRequest(
                    order.getId(),
                    supplier.hubId(),        // 출발 허브: 상품이 있는 허브
                    requester.hubId(),      // 도착 허브: 요청 업체의 관리 허브
                    requester.address(),    // 배송지: 요청 업체 주소
                    order.getReceiver()));
        } catch (Exception e) {
            log.error("배송 생성 실패 - orderId={}", order.getId(), e);
            try {
                hubClient.increaseStock(supplier.hubId(), product.productId(), request.getQuantity(), order.getId());
            } catch (Exception ex) {
                log.error("배송 생성 실패 후 재고 보상 실패 - orderId={}", order.getId(), ex);
            }
            fail(order);
            throw new BaseException(ErrorCode.DELIVERY_CREATE_FAILED);
        }

        order.complete(deliveryId);
        orderRepository.save(order);

        try {
            slackNotifyOrderCreated(orderer, supplier, order);
        } catch (Exception e) {
            log.error("slack 알림 생성 실패 - orderId={}", order.getId(), e);
        }

        return OrderResponse.from(order);
    }

    public OrderResponse searchOrder(UUID userId, String role, UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new BaseException(ErrorCode.ORDER_NOT_FOUND));

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
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new BaseException(ErrorCode.ORDER_NOT_FOUND));

        // 관리자가 아니면 상품 담당자만 가능
        if (!isMaster(role)){
            CompanyInfo supplier = companyClient.getSupplyCompany(order.getSupplierCompanyId());
            if(!userId.equals(supplier.managerId())) {
                throw new BaseException(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED);
            }
        }

        return OrderResponse.from(order);
    }

    public PagedResponse<OrderResponse> searchReceivedPageOrders(UUID userId, String role, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // 관리자는 전체, 상품 담당자는 본인 업체가 공급 업체인 주문만 조회
        if (isMaster(role)) {
            return PagedResponse.from(orderRepository.findAllByDeletedAtIsNull(pageable)
                    .map(OrderResponse::from));
        }

        CompanyInfo company;
        try {
            company = companyClient.getCompany(userId);
        } catch (Exception e) {
            throw new BaseException(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED);
        }

        Page<Order> orders = orderRepository
                .findAllBySupplierCompanyIdAndStatusNotAndDeletedAtIsNull(company.companyId(), OrderStatus.FAILED, pageable);

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
        validateDeliveryReady(order);

        ProductInfo product = companyClient.getProduct(order.getProductId());
        CompanyInfo supplier = companyClient.getSupplyCompany(product.supplierCompanyId());

        // 1. 재고 조정
        adjustStock(order.getId(), supplier, product, order.getQuantity(), request.getQuantity());

        // 2. 배송 수정, 실패하면 재고 조정을 되돌림
        try {
            deliveryClient.updateDelivery(order.getDeliveryId(), new DeliveryUpdateRequest(
                    request.getDeliveryAddress(),
                    request.getReceiver()));
        } catch (Exception e) {
            log.error("배송 수정 실패 - orderId={}", order.getId(), e);
            try {
                adjustStock(order.getId(), supplier, product, request.getQuantity(), order.getQuantity());
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

        CompanyInfo supplier = companyClient.getSupplyCompany(order.getSupplierCompanyId());

        if (!isMaster(role)
                && !userId.equals(order.getCreatedBy())
                && !userId.equals(supplier.managerId())) {
            throw new BaseException(ErrorCode.ORDER_DELETE_DENIED);
        }

        // 재고 부족 등으로 이미 취소된 주문은 재고와 배송 처리 없이 삭제만
        if (order.isModifiable()) {
            validateDeliveryReady(order);
            ProductInfo product = companyClient.getProduct(order.getProductId());

            deliveryClient.cancelDelivery(order.getDeliveryId());
            // 배송은 이미 취소됐으므로 재고 복구가 실패해도 주문은 취소 처리하고 기록을 남김
            try {
                hubClient.increaseStock(supplier.hubId(), product.productId(), order.getQuantity(), order.getId());
            } catch (Exception e) {
                log.error("주문 취소 후 재고 복구 실패 - orderId={}", order.getId(), e);
            }
        }

        order.delete(userId);
        orderRepository.save(order);
    }

    //---------------------------------------------------------
    // 주문자와 공급 업체 담당자를 멘션해서 알림 채널에 발송
    private void slackNotifyOrderCreated(UserInfo orderer, CompanyInfo supplier, Order order) {
        try {
            UserInfo supplierManager = userClient.getUser(supplier.managerId());
            slackWebhookSender.send(buildOrderCreatedMessage(order, orderer.slackId(), supplierManager.slackId()));
        } catch (Exception e) {
            log.warn("주문 생성 Slack 알림 실패 - orderId={}", order.getId(), e);
        }
    }

    private String buildOrderCreatedMessage(Order order, String ordererSlackId, String managerSlackId) {
        return """
                [주문 생성 알림] %s %s
                주문번호: %s
                상품 ID: %s
                수량: %d
                수령인: %s
                요청사항: %s
                주문일시: %s
                """.formatted(
                mention(ordererSlackId),
                mention(managerSlackId),
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getReceiver(),
                order.getRequestMessage() == null ? "-" : order.getRequestMessage(),
                order.getCreatedAt());
    }

    // slackId가 없으면 멘션 생략
    private String mention(String slackId) {
        return (slackId == null || slackId.isBlank()) ? "" : "<@" + slackId + ">";
    }

    private void fail(Order order) {
        order.fail();
        orderRepository.save(order);
    }

    private boolean isMaster(String role) {
        return MASTER_ROLE.equalsIgnoreCase(role);
    }

    private Order findActiveOrder(UUID orderId) {
        return orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new BaseException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateDeliveryReady(Order order) {
        DeliveryInfo delivery = deliveryClient.getDelivery(order.getDeliveryId());
        if (!MODIFIABLE_DELIVERY_STATUS.equals(delivery.status())) {
            throw new BaseException(ErrorCode.ORDER_DELIVERY_STARTED);
        }
    }

    private void adjustStock(UUID orderId, CompanyInfo supplier, ProductInfo product, int quantity, int updateQuantity) {
        int diff = updateQuantity - quantity;
        if (diff > 0 && !hubClient.decreaseStock(supplier.hubId(), product.productId(), diff, orderId)) {
            throw new BaseException(ErrorCode.OUT_OF_STOCK_FOR_UPDATE);
        }
        if (diff < 0) {
            hubClient.increaseStock(supplier.hubId(), product.productId(), -diff, orderId);
        }
    }
}
