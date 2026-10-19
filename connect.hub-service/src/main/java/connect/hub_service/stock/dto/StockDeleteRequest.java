package connect.hub_service.stock.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// 상품 삭제(개별 상품 또는 업체 삭제에 따른 소속 상품 일괄 삭제) 시 Company가 보내는 삭제 대상 상품 ID 목록.
public record StockDeleteRequest(
        @NotEmpty(message = "상품 ID 목록은 1개 이상이어야 합니다.") List<@NotNull(message = "상품 ID는 null일 수 없습니다.") UUID> productIds
) {
}
