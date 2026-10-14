package connect.order_service.domain.client.dto;

public record DeliveryUpdateRequest(
        String deliveryAddress,
        String receiver
) {
}