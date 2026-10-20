package connect.user_service.domain.user.dto.request;

import java.util.UUID;

public record StockAdjustRequest(
        UUID adjustmentId,
        UUID orderId,
        UUID hubId,
        UUID productId,
        Integer quantityDelta) {

}
