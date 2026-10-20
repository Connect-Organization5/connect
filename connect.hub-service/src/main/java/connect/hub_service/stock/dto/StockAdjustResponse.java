package connect.hub_service.stock.dto;

import java.util.UUID;

// processedDelta: 이 adjustmentId가 최초 처리될 때 적용한 증감량(재호출 시에도 최초 값)
// allocatedQuantity: 응답 시점에 주문이 점유한 수량(DECREASE + 모든 ADJUST 합계, 복원 완료면 0)
// currentStockQuantity: 응답 시점의 실제 재고 수량
public record StockAdjustResponse(
        UUID adjustmentId,
        UUID orderId,
        UUID stockId,
        Integer processedDelta,
        Integer allocatedQuantity,
        Integer currentStockQuantity,
        boolean alreadyProcessed
) {
}
