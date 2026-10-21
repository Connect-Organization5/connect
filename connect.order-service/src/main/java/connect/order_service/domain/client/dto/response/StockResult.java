package connect.order_service.domain.client.dto.response;

// 재고 차감·조정 결과. 거절 사유가 확정된 경우만 담고, 결과를 알 수 없는 실패는 예외로 던짐
public enum StockResult {
    SUCCESS,
    OUT_OF_STOCK,   // 재고 부족
    DISCONTINUED,   // 판매 중단 상품
    NOT_READY       // 판매 준비 중인 상품
}
