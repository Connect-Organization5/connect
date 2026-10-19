package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.response.UserResponse;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import connect.user_service.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /users/{userId} - 회원 단건 조회 성공")
    void getUser_success() throws Exception {
        // given
        UserResponse response = new UserResponse(1L, "tester", "encodedPw", "tester@test.com", "COMPANY", 1L, "slack-1");
        given(userService.getUser(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/{userId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.email").value("tester@test.com"))
                .andExpect(jsonPath("$.affiliationType").value("COMPANY"))
                .andExpect(jsonPath("$.affiliationId").value(1L))
                .andExpect(jsonPath("$.slackId").value("slack-1"));
    }

    @Test
    @DisplayName("GET /users/{userId} - 존재하지 않는 회원이면 404")
    void getUser_notFound() throws Exception {
        // given
        given(userService.getUser(999L)).willThrow(new BaseException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/users/{userId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("GET /users/{userId} - userId가 숫자가 아니면 400")
    void getUser_invalidUserId() throws Exception {
        mockMvc.perform(get("/users/{userId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
