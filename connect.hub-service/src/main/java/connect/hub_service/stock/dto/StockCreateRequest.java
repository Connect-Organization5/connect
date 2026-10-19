package connect.hub_service.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// companyId는 상품의 소속 업체다. Hub는 이 값으로 Company에 업체·상품 존재와 소속 허브를 확인한다.
public record StockCreateRequest(
        @NotNull(message = "업체 ID는 필수입니다.") UUID companyId,
        @NotNull(message = "허브 ID는 필수입니다.") UUID hubId,
        @NotNull(message = "상품 ID는 필수입니다.") UUID productId,
        @NotNull(message = "수량은 필수입니다.") @Min(value = 0, message = "수량은 0 이상이어야 합니다.") Integer quantity
) {
}
