package connect.company_service.client.hub.dto;

import java.util.List;
import java.util.UUID;

// Hub Service POST /internal/hubs/stocks/delete 요청 본문
// 빈 목록이면 호출하는 쪽(Product/Company 삭제 로직)에서 호출을 생략한다
public record StockDeleteRequest(
        List<UUID> productIds
) {
}
