package connect.order_service.domain.client.dto.response;

public record DeliveryUpdateRequest(
        String deliveryAddress,
        String receiver
) {
}