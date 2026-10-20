package connect.user_service.domain.user.dto.request;

import java.util.UUID;

public record StockDecreaseRequest(
        UUID orderId,
        UUID hubId,
        UUID productId,
        Integer quantity) {

}
