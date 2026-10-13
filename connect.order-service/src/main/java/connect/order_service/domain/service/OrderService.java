package connect.order_service.domain.service;

import connect.order_service.domain.client.clientInterface.*;
import connect.order_service.domain.client.dto.CompanyInfo;
import connect.order_service.domain.client.dto.DeliveryCreateRequest;
import connect.order_service.domain.client.dto.ProductInfo;
import connect.order_service.domain.client.dto.UserInfo;
import connect.order_service.domain.dto.CreateOrderRequest;
import connect.order_service.domain.dto.OrderResponse;
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


}
