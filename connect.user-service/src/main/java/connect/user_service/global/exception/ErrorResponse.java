package connect.user_service.global.exception;

// 에러 응답 공통 형식 (Company Service 와 동일하게 code, message 만 반환)
// HTTP 상태는 본문이 아니라 실제 응답 상태 코드로 전달한다 (ErrorCode.getStatus())
public record ErrorResponse(
        String code,
        String message
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
