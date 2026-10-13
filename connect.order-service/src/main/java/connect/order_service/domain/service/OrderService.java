package connect.order_service.domain.service;

import connect.order_service.domain.client.clientInterface.*;
import connect.order_service.domain.client.dto.*;
import connect.order_service.domain.dto.CreateOrderRequest;
import connect.order_service.domain.dto.OrderResponse;
import connect.order_service.domain.dto.UpdateOrderRequest;
import connect.order_service.domain.entity.Order;
import connect.order_service.domain.repository.OrderRepository;
import connect.order_service.global.dto.PagedResponse;
import connect.order_service.global.exception.BaseException;
import connect.order_service.global.exception.ErrorCode;
import connect.order_service.global.slack.SlackWebhookSender;
import jakarta.validation.Valid;
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
    private final ProductClient productClient;
    private final HubClient hubClient;
    private final DeliveryClient deliveryClient;
    private final SlackWebhookSender slackWebhookSender;

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String MODIFIABLE_DELIVERY_STATUS = "READY";

    public OrderResponse createOrder(UUID userId, @Valid CreateOrderRequest request) {
        UserInfo orderer = userClient.getUser(userId);
        CompanyInfo requester = companyClient.getCompany(orderer.companyId());
        ProductInfo product = productClient.getProduct(request.getProductId());

        Order order = Order.builder()
                .requesterCompanyId(requester.companyId())
                .supplierCompanyId(product.supplierCompanyId())
                .productId(product.productId())
                .quantity(request.getQuantity())
                .requestMessage(request.getRequestMessage())
                .receiver(request.getReceiver())
                .build();

        boolean decreased = hubClient.decreaseStock(
                product.hubId(), product.productId(), request.getQuantity(), order.getId());
        if (!decreased) {
            throw new BaseException(ErrorCode.OUT_OF_STOCK);
        }

        UUID deliveryId;
        try {
            deliveryId = deliveryClient.createDelivery(new DeliveryCreateRequest(
                    order.getId(),
                    product.hubId(),        // 출발 허브: 상품이 있는 허브
                    requester.hubId(),      // 도착 허브: 요청 업체의 관리 허브
                    requester.address(),    // 배송지: 요청 업체 주소
                    order.getReceiver()));
        } catch (Exception e) {
            log.error("배송 생성 실패 - orderId={}", order.getId(), e);
            hubClient.increaseStock(product.hubId(), product.productId(), request.getQuantity(), order.getId());
            throw new BaseException(ErrorCode.DELIVERY_CREATE_FAILED);
        }

        order.complete(deliveryId);
        orderRepository.save(order);

        notifyOrderCreated(orderer, order);

        return OrderResponse.from(order);
    }

    public OrderResponse searchOrder(UUID userId, String role, UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new BaseException(ErrorCode.ORDER_NOT_FOUND));

        // 관리자가 아니면 본인 주문만 조회 가능
        if (!isAdmin(role) && !userId.equals(order.getCreatedBy())) {
            throw new BaseException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        return OrderResponse.from(order);
    }

    public PagedResponse<OrderResponse> searchPageOrders(UUID userId, String role, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // 관리자는 전체, 일반 사용자는 본인 주문만 조회 (취소된 주문 포함)
        Page<Order> orders = isAdmin(role)
                ? orderRepository.findAllByDeletedAtIsNull(pageable)
                : orderRepository.findAllByCreatedByAndDeletedAtIsNull(userId, pageable);

        return PagedResponse.from(orders.map(OrderResponse::from));
    }

    public OrderResponse updateOrder(UUID userId, String role, UUID orderId, UpdateOrderRequest request) {
        Order order = findActiveOrder(orderId);
        validateOrderOwner(userId, role, order);
        validateDeliveryReady(order);

        ProductInfo oldProduct = productClient.getProduct(order.getProductId());
        ProductInfo newProduct = productClient.getProduct(request.getProductId());

        // 1. 재고 조정
        adjustStock(order.getId(), oldProduct, order.getQuantity(), newProduct, request.getQuantity());

        // 2. 배송 수정, 실패하면 재고 조정을 되돌림
        try {
            deliveryClient.updateDelivery(order.getDeliveryId(), new DeliveryUpdateRequest(
                    newProduct.hubId(),
                    request.getDeliveryAddress(),
                    request.getReceiver()));
        } catch (Exception e) {
            log.error("배송 수정 실패 - orderId={}", order.getId(), e);
            adjustStock(order.getId(), newProduct, request.getQuantity(), oldProduct, order.getQuantity());
            throw new BaseException(ErrorCode.DELIVERY_UPDATE_FAILED);
        }

        // 3. 주문 수정
        order.update(newProduct.productId(), newProduct.supplierCompanyId(), request.getQuantity(),
                request.getRequestMessage(), request.getReceiver());
        return OrderResponse.from(orderRepository.save(order));
    }



    //---------------------------------------------------------
    // 주문자와 공급 업체 담당자를 멘션해서 알림 채널에 발송
    private void notifyOrderCreated(UserInfo orderer, Order order) {
        try {
            CompanyInfo supplier = companyClient.getCompany(order.getSupplierCompanyId());
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

    private boolean isAdmin(String role) {
        return ADMIN_ROLE.equalsIgnoreCase(role);
    }

    private Order findActiveOrder(UUID orderId) {
        return orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new BaseException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateOrderOwner(UUID userId, String role, Order order) {
        if (isAdmin(role) || userId.equals(order.getCreatedBy())) {
            return;
        }
        CompanyInfo supplier = companyClient.getCompany(order.getSupplierCompanyId());
        if (!userId.equals(supplier.managerId())) {
            throw new BaseException(ErrorCode.ORDER_MODIFY_DENIED);
        }
    }

    private void validateDeliveryReady(Order order) {
        DeliveryInfo delivery = deliveryClient.getDelivery(order.getDeliveryId());
        if (!MODIFIABLE_DELIVERY_STATUS.equals(delivery.status())) {
            throw new BaseException(ErrorCode.ORDER_DELIVERY_STARTED);
        }
    }

    private void adjustStock(UUID orderId, ProductInfo product, int Quantity, ProductInfo updateProduct, int updateQuantity) {
        if (product.productId().equals(updateProduct.productId())) {
            int diff = updateQuantity - Quantity;
            if (diff > 0 && !hubClient.decreaseStock(updateProduct.hubId(), updateProduct.productId(), diff, orderId)) {
                throw new BaseException(ErrorCode.OUT_OF_STOCK_FOR_UPDATE);
            }
            if (diff < 0) {
                hubClient.increaseStock(updateProduct.hubId(), updateProduct.productId(), -diff, orderId);
            }
            return;
        }
        // 상품 변경: 새 상품 차감이 성공한 뒤에 기존 상품을 복구
        if (!hubClient.decreaseStock(updateProduct.hubId(), updateProduct.productId(), updateQuantity, orderId)) {
            throw new BaseException(ErrorCode.OUT_OF_STOCK_FOR_UPDATE);
        }
        hubClient.increaseStock(product.hubId(), product.productId(), Quantity, orderId);
    }
}
