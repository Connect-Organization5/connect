package connect.order_service.domain.client.dto;

import java.util.UUID;

public record DeliveryInfo(
        UUID deliveryId,
        String status
) {
}
