package connect.hub_service.stock.dto;

import java.util.UUID;

// inboundQuantity: 이 inboundId로 더한 수량(재요청이어도 최초 값)
// currentQuantity: 응답 시점의 현재 재고 수량(다른 입고·주문 때문에 최초 처리 직후와 다를 수 있음)
// alreadyProcessed: 같은 inboundId로 이미 처리된 입고의 재요청이면 true(수량을 다시 더하지 않음)
public record StockInboundResponse(
        UUID inboundId,
        UUID stockId,
        UUID hubId,
        UUID productId,
        Integer inboundQuantity,
        Integer currentQuantity,
        boolean alreadyProcessed
) {
}
