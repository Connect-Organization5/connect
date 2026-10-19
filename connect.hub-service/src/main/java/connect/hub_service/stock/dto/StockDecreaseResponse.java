package connect.hub_service.stock.dto;

import java.util.UUID;

// remainingQuantity는 응답 시점의 현재 재고 수량이며, 다른 주문 때문에 최초 차감 직후 수량과 다를 수 있다.
public record StockDecreaseResponse(
        UUID orderId,
        UUID stockId,
        Integer processedQuantity,
        Integer remainingQuantity,
        boolean alreadyProcessed
) {
}
