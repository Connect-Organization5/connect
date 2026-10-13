package connect.order_service.domain.dto;

import connect.order_service.domain.entity.Order;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID requesterCompanyId,
        UUID supplierCompanyId,
        UUID productId,
        UUID deliveryId,
        Integer quantity,
        String requestMessage,
        String receiver,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getRequesterCompanyId(),
                order.getSupplierCompanyId(),
                order.getProductId(),
                order.getDeliveryId(),
                order.getQuantity(),
                order.getRequestMessage(),
                order.getReceiver(),
                order.getCreatedAt()
        );
    }
}
