package connect.hub_service.global.exception;

import connect.hub_service.global.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 서비스 코드에서 throw new BaseException(ErrorCode.XXX)로 던질 때
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
    }

    // @RequestHeader로 받기로 한 헤더(X-User-Id, X-User-Role)가 요청에 없을 때
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(ErrorCode.MISSING_HEADER.getStatus())
                .body(ApiResponse.error(ErrorCode.MISSING_HEADER.getMessage() + " (" + e.getHeaderName() + ")"));
    }

    // @Valid @RequestBody DTO가 검증 어노테이션을 통과하지 못할 때
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ErrorCode.VALIDATION_FAILED.getMessage());

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiResponse.error(message));
    }

    // 요청 JSON 형식이 깨졌거나 본문이 없을 때, 파라미터 타입이 맞지 않을 때, 파라미터가 UTF-8로 디코딩되지 않을 때
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            InvalidParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getMessage()));
    }

    // 위 어디에도 해당하지 않는 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // 없는 URL(404), 지원하지 않는 메서드(405) 같은 Spring 표준 웹 예외는 원래 상태 코드를 유지한다
        if (e instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(ApiResponse.error(ErrorCode.UNSUPPORTED_REQUEST.getMessage()));
        }

        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
