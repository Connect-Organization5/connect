package connect.hub_service.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 입고(재고 추가) 요청. hubId·productId로 기존 재고를 찾아 quantity만큼 현재 수량에 더한다.
public record StockInboundRequest(
        @NotNull(message = "허브 ID는 필수입니다.") UUID hubId,
        @NotNull(message = "상품 ID는 필수입니다.") UUID productId,
        @NotNull(message = "입고 수량은 필수입니다.") @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다.") Integer quantity
) {
}
