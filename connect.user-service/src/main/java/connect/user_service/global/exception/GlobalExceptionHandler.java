package connect.user_service.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        log.warn("BaseException: {}", e.getMessage());
        return toResponse(e.getErrorCode());
    }

    // DB 제약 조건 위반 (동시 요청으로 중복 체크를 통과한 경우 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMostSpecificCause().getMessage());
        return toResponse(ErrorCode.DUPLICATE_RESOURCE);
    }

    // 비관적 락 대기 시간 초과 또는 데드락 (같은 소속 MASTER 를 동시에 삭제/강등하는 경우 등)
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handlePessimisticLockingFailure(PessimisticLockingFailureException e) {
        log.warn("PessimisticLockingFailure: {}", e.getMostSpecificCause().getMessage());
        return toResponse(ErrorCode.LOCK_CONFLICT);
    }

    // @Valid 유효성 검사 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return toResponse(ErrorCode.VALIDATION_FAILED, message);
    }

    // @RequestParam, @PathVariable 의 @Min, @Max 등 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        String message = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return toResponse(ErrorCode.VALIDATION_FAILED, message);
    }

    // 필수 헤더 누락
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return toResponse(ErrorCode.MISSING_HEADER);
    }

    // 잘못된 요청 (파라미터 누락, 타입 불일치, JSON 파싱 실패)
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidInput(Exception e) {
        log.warn("Invalid input: {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_INPUT);
    }

    // 지원하지 않는 HTTP 메서드 → HTTP 표준대로 405 유지 (본문 code 는 UNSUPPORTED_REQUEST)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(e.getStatusCode()).body(ErrorResponse.of(ErrorCode.UNSUPPORTED_REQUEST));
    }

    // 그 외 예상하지 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        // 없는 URL(404) 같은 Spring 표준 웹 예외는 원래 상태 코드를 유지 (Company Service 와 동일)
        if (e instanceof org.springframework.web.ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(ErrorResponse.of(ErrorCode.UNSUPPORTED_REQUEST));
        }

        // 원인 추적용 로그만 남기고 응답에는 예외 메시지, SQL, Stack Trace 를 노출하지 않음
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return toResponse(errorCode, errorCode.getMessage());
    }

    // HTTP 상태는 ErrorCode 에 정의된 값으로 응답 상태 코드에 설정 (본문에는 넣지 않음)
    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, message));
    }
}
