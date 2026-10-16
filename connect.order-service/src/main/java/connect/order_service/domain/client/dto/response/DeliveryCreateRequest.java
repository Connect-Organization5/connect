package connect.order_service.domain.client.dto.response;

import java.util.UUID;

public record DeliveryCreateRequest(
        UUID orderId,
        UUID requestUserId,
        UUID departureHubId,
        UUID arrivalHubId,
        String deliveryAddress,
        String receiver
) {
}
