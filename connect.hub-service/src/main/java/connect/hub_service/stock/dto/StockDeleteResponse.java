package connect.hub_service.stock.dto;

// deletedCount는 이번 호출에서 새로 논리 삭제한 재고 건수다(이미 삭제된 재고는 제외).
public record StockDeleteResponse(int deletedCount) {
}
