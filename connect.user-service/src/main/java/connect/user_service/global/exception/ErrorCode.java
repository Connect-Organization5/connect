package connect.user_service.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 기본 설정
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "유효성 검사에 실패했습니다."),
    MISSING_HEADER(HttpStatus.BAD_REQUEST, "필수 헤더가 누락되었습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNSUPPORTED_REQUEST(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),

    // User
    USER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "유저에 대한 권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 유저입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 이메일 주소입니다."),
    SLACK_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 Slack ID 입니다."),
    LAST_MASTER_CANNOT_BE_DELETED(HttpStatus.CONFLICT, "마지막 MASTER 는 삭제할 수 없습니다."),
    LAST_MASTER_CANNOT_BE_DEMOTED(HttpStatus.CONFLICT, "마지막 MASTER 는 USER 로 변경할 수 없습니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
    LOCK_CONFLICT(HttpStatus.CONFLICT, "다른 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요."),
    HUB_NOT_FOUND(HttpStatus.NOT_FOUND, "허브를 찾을 수 없습니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "업체를 찾을 수 없습니다."),
    AFFILIATION_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "업체 or 허브를 찾을 수 없습니다");

    private final HttpStatus status;
    private final String message;
}