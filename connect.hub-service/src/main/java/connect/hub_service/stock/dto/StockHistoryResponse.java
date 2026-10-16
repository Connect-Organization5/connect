package connect.hub_service.stock.dto;

import connect.hub_service.stock.entity.StockHistory;
import connect.hub_service.stock.entity.StockHistoryType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StockHistoryResponse(UUID orderId, List<Item> histories) {

    public record Item(UUID historyId, UUID stockId, StockHistoryType type, Integer quantity, LocalDateTime createdAt) {

        static Item from(StockHistory history) {
            return new Item(history.getId(), history.getStock().getId(), history.getType(),
                    history.getQuantity(), history.getCreatedAt());
        }
    }

    public static StockHistoryResponse of(UUID orderId, List<StockHistory> histories) {
        return new StockHistoryResponse(orderId, histories.stream().map(Item::from).toList());
    }
}
