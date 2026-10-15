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
    ;

    private final HttpStatus status;
    private final String message;
}
