package connect.order_service.domain.client.dto.request;

import java.util.UUID;

public record DeliveryCreateRequest(
        UUID orderId,
        UUID ordererId,
        UUID departureHubId,
        UUID arrivalHubId,
        String deliveryAddress,
        String receiver
) {
}
