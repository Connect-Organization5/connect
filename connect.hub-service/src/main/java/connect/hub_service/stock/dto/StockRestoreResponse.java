package connect.hub_service.stock.dto;

import java.util.UUID;

public record StockRestoreResponse(
        UUID orderId,
        UUID stockId,
        Integer restoredQuantity,
        Integer currentQuantity,
        boolean alreadyProcessed
) {
}
