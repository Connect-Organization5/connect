package connect.order_service.domain.client.dto;

import java.util.UUID;

public record DeliveryUpdateRequest(
        UUID departureHubId,
        String deliveryAddress,
        String receiver
) {
}