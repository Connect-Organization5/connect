package connect.hub_service.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import connect.hub_service.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private Boolean success;
    // 실패 응답에만 들어가는 ErrorCode 이름(예: INSUFFICIENT_STOCK). 호출하는 서비스가 같은 상태 코드 안에서 원인을 구분하는 데 쓴다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    // 메시지에 요청별 정보(누락된 헤더 이름, 검증 실패 필드 메시지 등)를 붙일 때
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(errorCode.name())
                .message(message)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
