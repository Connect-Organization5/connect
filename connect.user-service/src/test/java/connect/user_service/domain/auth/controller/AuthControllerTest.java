package connect.user_service.domain.auth.controller;

import connect.user_service.domain.auth.service.AuthService;
import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String VALID_BODY = """
            {
              "username": "tester",
              "password": "password1!",
              "email": "tester@test.com",
              "affiliationType": "COMPANY",
              "affiliationId": 1,
              "slackId": "slack-1"
            }
            """;

    @Mock
    private AuthService authService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ResultActions signup(String body) throws Exception {
        return mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("POST /auth/signup - 성공 시 201 과 access_token 반환")
    void signup_success() throws Exception {
        // given
        given(authService.createAccessToken("tester")).willReturn("test-token");

        // when & then
        signup(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").value("test-token"));
        then(userService).should().signup(any(SignUpRequestDto.class));
    }

    @Test
    @DisplayName("POST /auth/signup - username 중복이면 409 USER_ALREADY_EXISTS")
    void signup_duplicateUsername() throws Exception {
        willThrow(new BaseException(ErrorCode.USER_ALREADY_EXISTS)).given(userService).signup(any());

        signup(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
        then(authService).should(never()).createAccessToken(anyString());
    }

    @Test
    @DisplayName("POST /auth/signup - email 중복이면 409 EMAIL_ALREADY_EXISTS")
    void signup_duplicateEmail() throws Exception {
        willThrow(new BaseException(ErrorCode.EMAIL_ALREADY_EXISTS)).given(userService).signup(any());

        signup(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /auth/signup - slackId 중복이면 409 SLACK_ID_ALREADY_EXISTS")
    void signup_duplicateSlackId() throws Exception {
        willThrow(new BaseException(ErrorCode.SLACK_ID_ALREADY_EXISTS)).given(userService).signup(any());

        signup(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLACK_ID_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /auth/signup - DB 제약 조건 위반이면 409 DUPLICATE_RESOURCE")
    void signup_dataIntegrityViolation() throws Exception {
        willThrow(new DataIntegrityViolationException("duplicate key")).given(userService).signup(any());

        signup(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /auth/signup - 필수값 누락이면 400 VALIDATION_FAILED")
    void signup_missingRequiredFields() throws Exception {
        String body = """
                {
                  "username": "tester",
                  "password": "password1!",
                  "email": "tester@test.com"
                }
                """;

        signup(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("affiliationType")))
                .andExpect(jsonPath("$.message").value(containsString("affiliationId")))
                .andExpect(jsonPath("$.message").value(containsString("slackId")));
        then(userService).should(never()).signup(any());
    }

    @Test
    @DisplayName("POST /auth/signup - 형식 오류(짧은 username, 잘못된 email)면 400 VALIDATION_FAILED")
    void signup_invalidFormat() throws Exception {
        String body = VALID_BODY
                .replace("\"tester\"", "\"ab\"")
                .replace("tester@test.com", "not-an-email");

        signup(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("username")))
                .andExpect(jsonPath("$.message").value(containsString("email")));
        then(userService).should(never()).signup(any());
    }

    @Test
    @DisplayName("POST /auth/signup - JSON 형식이 잘못되면 400 INVALID_INPUT")
    void signup_malformedJson() throws Exception {
        signup("{ invalid json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).signup(any());
    }

    // ===== POST /auth/login =====

    private ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("POST /auth/login - 성공 시 200 과 DB 회원 정보로 만든 access_token 반환")
    void login_success() throws Exception {
        // given
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, "COMPANY", 1L, "slack-1");
        given(userService.login(any(LoginRequestDto.class))).willReturn(user);
        given(authService.createAccessToken("tester")).willReturn("test-token");

        // when & then
        login("""
                { "username": "tester", "password": "password1!" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("test-token"));
    }

    @Test
    @DisplayName("POST /auth/login - 아이디 또는 비밀번호가 틀리면 401 INVALID_CREDENTIALS")
    void login_invalidCredentials() throws Exception {
        // given
        given(userService.login(any(LoginRequestDto.class)))
                .willThrow(new BaseException(ErrorCode.INVALID_CREDENTIALS));

        // when & then
        login("""
                { "username": "tester", "password": "wrong-password" }
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.getMessage()));
        then(authService).should(never()).createAccessToken(anyString());
    }

    @Test
    @DisplayName("POST /auth/login - username, password 누락이면 400 VALIDATION_FAILED")
    void login_missingFields() throws Exception {
        login("""
                { "username": "" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("username")))
                .andExpect(jsonPath("$.message").value(containsString("password")));
        then(userService).should(never()).login(any());
        then(authService).should(never()).createAccessToken(anyString());
    }

    @Test
    @DisplayName("POST /auth/login - JSON 형식이 잘못되면 400 INVALID_INPUT")
    void login_malformedJson() throws Exception {
        login("{ invalid json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).login(any());
    }
}
