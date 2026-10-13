package connect.order_service.domain.entity;

public enum OrderStatus {
    PENDING,    // 저장됨, 재고 차감 및 배송 생성 전
    CREATED,    // 재고 차감과 배송 생성 완료
    CANCELED    // 재고 부족 또는 배송 생성 실패
}
