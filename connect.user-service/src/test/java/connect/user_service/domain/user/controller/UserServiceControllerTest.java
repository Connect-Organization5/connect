package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.response.UpdateUserResponse;
import connect.user_service.domain.user.entity.AffiliationType;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserServiceControllerTest {

    private static final String URL = "/internal/users/{userId}";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");

    @Mock
    private UserService userService;

    @InjectMocks
    private UserServiceController userServiceController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userServiceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /internal/users/{userId} - userId 에 해당하는 회원 정보 반환, 비밀번호는 포함하지 않음")
    void sendUser_success() throws Exception {
        // given
        UpdateUserResponse response = new UpdateUserResponse(USER_ID, "tester", "tester@test.com", AffiliationType.COMPANY, AFFILIATION_ID, "slack-1");
        given(userService.sendUser(USER_ID)).willReturn(response);

        // when & then
        mockMvc.perform(get(URL, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.email").value("tester@test.com"))
                .andExpect(jsonPath("$.affiliationType").value("COMPANY"))
                .andExpect(jsonPath("$.affiliationId").value(AFFILIATION_ID.toString()))
                .andExpect(jsonPath("$.slackId").value("slack-1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /internal/users/{userId} - 존재하지 않는 회원이면 404 USER_NOT_FOUND")
    void sendUser_notFound() throws Exception {
        // given
        given(userService.sendUser(USER_ID)).willThrow(new BaseException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get(URL, USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /internal/users/{userId} - userId 가 UUID 형식이 아니면 400 INVALID_INPUT")
    void sendUser_invalidUserId() throws Exception {
        mockMvc.perform(get(URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).sendUser(any());
    }
}
