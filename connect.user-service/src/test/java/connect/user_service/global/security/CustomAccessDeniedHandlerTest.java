package connect.user_service.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler(objectMapper);

    @Test
    @DisplayName("Security 인가 단계에서 거부되면 403 과 USER_ACCESS_DENIED ErrorResponse 반환")
    void handle_returnsForbiddenErrorResponse() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.handle(request, response, new AccessDeniedException("denied"));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("USER_ACCESS_DENIED");
        assertThat(body.get("message").asString()).isEqualTo("유저에 대한 권한이 없습니다.");
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message");
    }
}
