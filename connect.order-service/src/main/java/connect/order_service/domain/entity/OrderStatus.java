package connect.order_service.domain.entity;

public enum OrderStatus {
    PENDING,    // 저장됨, 재고 차감 및 배송 생성 전
    CREATED,    // 재고 차감과 배송 생성 완료
    CANCELED,    // 주문 취소
    FAILED      // 주문 실패
}
