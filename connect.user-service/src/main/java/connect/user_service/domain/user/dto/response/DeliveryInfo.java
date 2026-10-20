package connect.user_service.domain.user.dto.response;

import java.util.UUID;

public record DeliveryInfo(
        UUID id,
        String deliveryStatus
) {
}
