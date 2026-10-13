package connect.order_service.domain.client.dto;

import java.util.UUID;

public record DeliveryCreateRequest(
        UUID orderId,
        UUID departureHubId,
        UUID arrivalHubId,
        String deliveryAddress,
        String receiver
) {
}
