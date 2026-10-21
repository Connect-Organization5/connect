package connect.hub_service.stock.dto;

import connect.hub_service.stock.entity.StockInbound;

import java.time.LocalDateTime;
import java.util.UUID;

public record StockInboundHistoryResponse(UUID inboundId, Integer quantity, UUID inboundBy, LocalDateTime createdAt) {

    public static StockInboundHistoryResponse from(StockInbound inbound) {
        return new StockInboundHistoryResponse(
                inbound.getInboundId(), inbound.getQuantity(), inbound.getInboundBy(), inbound.getCreatedAt());
    }
}
