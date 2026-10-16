package connect.hub_service.stock.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 주문 재고 복원 요청. 복원 수량은 받지 않고 원본 DECREASE 이력의 수량을 사용한다.
public record StockRestoreRequest(
        @NotNull(message = "주문 ID는 필수입니다.") UUID orderId,
        @NotNull(message = "허브 ID는 필수입니다.") UUID hubId,
        @NotNull(message = "상품 ID는 필수입니다.") UUID productId
) {
}
