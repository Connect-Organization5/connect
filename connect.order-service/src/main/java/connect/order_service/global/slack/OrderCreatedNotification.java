package connect.order_service.global.slack;

import connect.order_service.domain.entity.Order;

import java.time.LocalDateTime;
import java.util.UUID;

// 비동기 스레드로 넘기는 알림 데이터 (엔티티 대신 필요한 값만 복사)
public record OrderCreatedNotification(
        UUID orderId,
        UUID productId,
        Integer quantity,
        String receiver,
        String requestMessage,
        LocalDateTime createdAt,
        UUID ordererId,
        UUID supplierManagerId
) {
    public static OrderCreatedNotification of(Order order, UUID ordererId, UUID supplierManagerId) {
        return new OrderCreatedNotification(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getReceiver(),
                order.getRequestMessage(),
                order.getCreatedAt(),
                ordererId,
                supplierManagerId);
    }
}
