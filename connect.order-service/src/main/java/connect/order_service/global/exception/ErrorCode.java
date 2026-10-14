package connect.order_service.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 주문만 조회할 수 있습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "소속 업체를 찾을 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족하여 주문에 실패했습니다."),
    STOCK_DECREASE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "재고 처리 중 오류가 발생하여 주문에 실패했습니다."),
    DELIVERY_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "배송 생성에 실패하여 주문에 실패했습니다."),
    ORDER_MODIFY_DENIED(HttpStatus.FORBIDDEN, "주문자 본인, 공급 업체 담당자 또는 관리자만 수정/취소할 수 있습니다."),
    ORDER_NOT_MODIFIABLE(HttpStatus.CONFLICT, "수정이 불가능한 주문입니다."),
    ORDER_DELIVERY_STARTED(HttpStatus.CONFLICT, "배송이 시작된 주문은 수정/취소할 수 없습니다."),
    OUT_OF_STOCK_FOR_UPDATE(HttpStatus.CONFLICT, "재고가 부족하여 주문을 수정할 수 없습니다."),
    DELIVERY_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "배송 수정에 실패했습니다."),
    ;


    private final HttpStatus status;
    private final String message;
}
