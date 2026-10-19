package connect.user_service.global.security;

import connect.user_service.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(objectMapper);

    @Test
    @DisplayName("인증 실패 시 401과 ErrorResponse JSON을 반환")
    void commence_returnsUnauthorizedErrorResponse() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(request, response, new InsufficientAuthenticationException("no token"));

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asString()).isEqualTo("인증이 필요합니다.");
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("JWT 필터가 INVALID_TOKEN 을 표시했으면 INVALID_TOKEN 으로 응답")
    void commence_withInvalidTokenAttribute() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        request.setAttribute(CustomAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(request, response, new InsufficientAuthenticationException("invalid token"));

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("INVALID_TOKEN");
        assertThat(body.get("message").asString()).isEqualTo(ErrorCode.INVALID_TOKEN.getMessage());
    }
}
