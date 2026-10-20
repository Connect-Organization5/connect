package connect.hub_service.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 주문 재고 차감 요청. 주문당 단일 상품(hubId, productId, quantity)만 받는다.
public record StockDecreaseRequest(
        @NotNull(message = "주문 ID는 필수입니다.") UUID orderId,
        @NotNull(message = "허브 ID는 필수입니다.") UUID hubId,
        @NotNull(message = "상품 ID는 필수입니다.") UUID productId,
        @NotNull(message = "차감 수량은 필수입니다.") @Min(value = 1, message = "차감 수량은 1 이상이어야 합니다.") Integer quantity
) {
}
