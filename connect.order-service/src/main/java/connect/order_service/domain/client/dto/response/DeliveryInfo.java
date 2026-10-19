package connect.order_service.domain.client.dto.response;

import java.util.UUID;

public record DeliveryInfo(
        UUID id,
        String deliveryStatus
) {
}
