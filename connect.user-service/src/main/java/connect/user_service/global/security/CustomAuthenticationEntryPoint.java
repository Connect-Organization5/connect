package connect.user_service.global.security;

import connect.user_service.global.exception.ErrorCode;
import connect.user_service.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 인증되지 않은 사용자가 보호된 자원에 접근하면 401 응답을 ErrorResponse 형식으로 반환
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // JwtAuthorizationFilter 에서 토큰 검증 실패 시 원인 ErrorCode 를 담아두는 request attribute 이름
    public static final String ERROR_CODE_ATTRIBUTE = "authErrorCode";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthorized request: {} {}", request.getMethod(), request.getRequestURI());

        ErrorCode errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE) instanceof ErrorCode code
                ? code
                : ErrorCode.UNAUTHORIZED;
        ErrorResponse errorResponse = ErrorResponse.of(errorCode);

        response.setStatus(errorResponse.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
