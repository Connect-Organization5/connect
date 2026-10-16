package connect.order_service.domain.client.dto.request;

import java.util.UUID;

public record StockDecreaseRequest (
        UUID orderId,
        UUID hubId,
        UUID productId,
        Integer quantity){

}
