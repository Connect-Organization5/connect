package connect.hub_service.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 입고(재고 추가) 요청. hubId·productId로 기존 재고를 찾아 quantity만큼 현재 수량에 더한다.
// companyId는 상품의 소속 업체이며, 요청자가 그 업체의 관리자인지와 소속 허브를 Company에 확인하는 데 쓴다.
// inboundId는 입고 한 건의 멱등 키다. 입고마다 새로 만들고, 같은 입고를 재시도할 때는 같은 값을 보낸다.
public record StockInboundRequest(
        @NotNull(message = "입고 ID는 필수입니다.") UUID inboundId,
        @NotNull(message = "업체 ID는 필수입니다.") UUID companyId,
        @NotNull(message = "허브 ID는 필수입니다.") UUID hubId,
        @NotNull(message = "상품 ID는 필수입니다.") UUID productId,
        @NotNull(message = "입고 수량은 필수입니다.") @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다.") Integer quantity
) {
}
