package connect.order_service.domain.client.dto.request;

import java.util.UUID;

public record StockAdjustRequest(
        UUID adjustmentId,
        UUID orderId,
        UUID hubId,
        UUID productId,
        Integer quantityDelta) {

}