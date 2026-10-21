package connect.hub_service.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // common
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "유효성 검사에 실패했습니다."),
    MISSING_HEADER(HttpStatus.BAD_REQUEST, "필수 헤더가 누락되었습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNSUPPORTED_REQUEST(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호 또는 크기가 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // hub
    HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다."),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "이동 경로를 찾을 수 없습니다."),

    // stock
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "재고를 찾을 수 없습니다."),
    STOCK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 허브·상품 재고입니다."),
    STOCK_DELETED(HttpStatus.CONFLICT, "삭제된 재고입니다."),
    STOCK_QUANTITY_OVERFLOW(HttpStatus.CONFLICT, "재고 수량이 허용 범위를 초과합니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    STOCK_REQUEST_CONFLICT(HttpStatus.CONFLICT, "같은 주문 ID로 처리된 기존 요청과 허브·상품·수량이 일치하지 않습니다."),
    STOCK_ALREADY_RESTORED(HttpStatus.CONFLICT, "이미 복원이 완료된 주문입니다."),
    DECREASE_HISTORY_NOT_FOUND(HttpStatus.CONFLICT, "복원할 차감 이력이 없습니다."),
    STOCK_HISTORY_MISMATCH(HttpStatus.CONFLICT, "요청한 허브·상품이 원본 차감 이력의 재고와 일치하지 않습니다."),
    STOCK_ADJUSTMENT_CONFLICT(HttpStatus.CONFLICT, "같은 조정 ID로 처리된 기존 요청과 주문·허브·상품·증감량이 일치하지 않습니다."),
    STOCK_ALLOCATION_UNDERFLOW(HttpStatus.CONFLICT, "조정 후 주문이 점유하는 재고 수량이 1 미만입니다."),
    INVALID_QUANTITY_DELTA(HttpStatus.BAD_REQUEST, "조정 수량은 0이 될 수 없습니다."),
    STOCK_HUB_MISMATCH(HttpStatus.CONFLICT, "요청한 허브가 업체의 소속 허브와 일치하지 않습니다."),
    STOCK_INBOUND_CONFLICT(HttpStatus.CONFLICT, "같은 입고 ID로 처리된 기존 요청과 허브·상품·수량이 일치하지 않습니다."),
    COMPANY_ID_REQUIRED(HttpStatus.BAD_REQUEST, "MASTER가 아니면 업체 ID(companyId)가 필요합니다."),

    // company 연동
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "업체를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "업체의 상품을 찾을 수 없습니다."),
    COMPANY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "업체 서비스 호출에 실패했습니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
