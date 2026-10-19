package connect.hub_service.stock.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 주문 수량 수정에 따른 재고 조정 요청. adjustmentId는 조정 작업의 멱등 키이며, 같은 작업을 재시도할 때는 같은 값을 보낸다.
//
// quantityDelta = 수정 후 주문 수량 - 수정 전 주문 수량 (Order가 계산한 diff)
//   양수: 주문 수량 증가 → Hub 재고 차감
//   음수: 주문 수량 감소 → 절댓값만큼 Hub 재고 증가
//   0: 조정할 수량이 없으므로 잘못된 요청
// 예: 3 → 1이면 -2(재고 2 증가), 1 → 5이면 +4(재고 4 차감)
public record StockAdjustRequest(
        @NotNull(message = "조정 ID는 필수입니다.") UUID adjustmentId,
        @NotNull(message = "주문 ID는 필수입니다.") UUID orderId,
        @NotNull(message = "허브 ID는 필수입니다.") UUID hubId,
        @NotNull(message = "상품 ID는 필수입니다.") UUID productId,
        @NotNull(message = "조정 수량은 필수입니다.") Integer quantityDelta
) {
}
