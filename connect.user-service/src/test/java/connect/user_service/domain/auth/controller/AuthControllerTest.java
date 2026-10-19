package connect.user_service.domain.auth.controller;

import connect.user_service.global.security.JwtUtil;
import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.response.UpdateUserResponse;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import connect.user_service.global.exception.GlobalExceptionHandler;
import connect.user_service.global.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");

    // 테스트용 고정 UUID (idOf(1) → 00000000-0000-0000-0000-000000000001)
    private static UUID idOf(long no) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(no));
    }

    private static final String VALID_BODY = """
            {
              "username": "tester",
              "password": "password1!",
              "email": "tester@test.com",
              "affiliationType": "COMPANY",
              "affiliationId": "00000000-0000-0000-0000-000000001000",
              "slackId": "slack-1"
            }
            """;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    private User loginUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                // @AuthenticationPrincipal 에 SecurityContext 의 로그인 회원을 주입
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        // JwtAuthorizationFilter 가 하는 것처럼 로그인 회원(MASTER)을 SecurityContext 에 저장
        loginUser = new User("master", "encodedPw", "master@test.com", UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID, "slack-master");
        loginUser.setId(idOf(1));
        UserDetailsImpl userDetails = new UserDetailsImpl(loginUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ResultActions signup(String body) throws Exception {
        return mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("POST /auth/signup - 성공 시 201 과 생성된 회원 정보 반환, 로그인 회원을 요청자로 전달하고 토큰은 발급하지 않음")
    void signup_success() throws Exception {
        // given
        UpdateUserResponse response = new UpdateUserResponse(idOf(2), "tester", "tester@test.com", AffiliationType.COMPANY, AFFILIATION_ID, "slack-1");
        given(userService.signup(any(SignUpRequestDto.class), same(loginUser))).willReturn(response);

        // when & then
        signup(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(idOf(2).toString()))
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.access_token").doesNotExist());
        then(jwtUtil).should(never()).createAccessToken(any(User.class));
    }

    @Test
    @DisplayName("POST /auth/signup - MASTER 가 아니거나 소속이 다르면 403 USER_ACCESS_DENIED")
    void signup_accessDenied() throws Exception {
        willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED)).given(userService).signup(any(), any());

        signup(VALID_BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /auth/signup - username 중복이면 409 USER_ALREADY_EXISTS")
    void signup_duplicateUsername() throws Exception {
        willThrow(new BaseException(ErrorCode.USER_ALREADY_EXISTS)).given(userService).signup(any(), any());

        signup(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
        then(jwtUtil).should(never()).createAccessToken(any(User.class));
    }

    @Test
    @DisplayName("POST /auth/signup - email 중복이면 409 EMAIL_ALREADY_EXISTS")
    void signup_duplicateEmail() throws Exception {
        willThrow(new BaseException(ErrorCode.EMAIL_ALREADY_EXISTS)).given(userService).signup(any(), any());

        signup(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /auth/signup - slackId 중복이면 409 SLACK_ID_ALREADY_EXISTS")
    void signup_duplicateSlackId() throws Exception {
        willThrow(new BaseException(ErrorCode.SLACK_ID_ALREADY_EXISTS)).given(userService).signup(any(), any());

        signup(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLACK_ID_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /auth/signup - DB 제약 조건 위반이면 409 DUPLICATE_RESOURCE")
    void signup_dataIntegrityViolation() throws Exception {
        willThrow(new DataIntegrityViolationException("duplicate key")).given(userService).signup(any(), any());

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
        then(userService).should(never()).signup(any(), any());
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
        then(userService).should(never()).signup(any(), any());
    }

    @Test
    @DisplayName("POST /auth/signup - JSON 형식이 잘못되면 400 INVALID_INPUT")
    void signup_malformedJson() throws Exception {
        signup("{ invalid json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).signup(any(), any());
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
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID, "slack-1");
        given(userService.login(any(LoginRequestDto.class))).willReturn(user);
        given(jwtUtil.createAccessToken(same(user))).willReturn("test-token");

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
        then(jwtUtil).should(never()).createAccessToken(any(User.class));
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
        then(jwtUtil).should(never()).createAccessToken(any(User.class));
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
