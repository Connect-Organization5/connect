package connect.company_service.client.hub.dto;

// Hub Service POST /internal/hubs/stocks/delete 응답의 data 부분 (삭제 대상이 없으면 0)
public record StockDeleteResponse(
        int deletedCount
) {
}
