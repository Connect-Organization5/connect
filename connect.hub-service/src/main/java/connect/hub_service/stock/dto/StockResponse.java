package connect.hub_service.stock.dto;

import connect.hub_service.stock.entity.Stock;

import java.util.UUID;

public record StockResponse(UUID stockId, UUID hubId, UUID productId, Integer quantity) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.getId(), stock.getHub().getId(), stock.getProductId(), stock.getQuantity());
    }
}
