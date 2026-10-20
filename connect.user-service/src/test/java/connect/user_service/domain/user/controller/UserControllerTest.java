package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.dto.PageResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 9, 0);

    // 테스트용 고정 UUID (idOf(1) → 00000000-0000-0000-0000-000000000001)
    private static UUID idOf(long no) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(no));
    }

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    private User loginUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                // @AuthenticationPrincipal 에 SecurityContext 의 로그인 회원을 주입
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        // JwtAuthorizationFilter 가 하는 것처럼 로그인 회원을 SecurityContext 에 저장
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

    @Test
    @DisplayName("GET /users/{userId} - 회원 단건 조회 성공, 로그인 회원을 요청자로 전달")
    void getUser_success() throws Exception {
        // given
        UserResponseDto response = new UserResponseDto(idOf(1), "tester", "tester@test.com", AffiliationType.COMPANY, AFFILIATION_ID, "slack-1", CREATED_AT);
        given(userService.getUser(eq(idOf(1)), same(loginUser))).willReturn(response);

        // when & then
        mockMvc.perform(get("/users/{userId}", idOf(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 정보를 조회했습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.id").value(idOf(1).toString()))
                .andExpect(jsonPath("$.data.username").value("tester"))
                .andExpect(jsonPath("$.data.email").value("tester@test.com"))
                .andExpect(jsonPath("$.data.affiliationType").value("COMPANY"))
                .andExpect(jsonPath("$.data.affiliationId").value(AFFILIATION_ID.toString()))
                .andExpect(jsonPath("$.data.slackId").value("slack-1"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-01-01T09:00:00"));
    }

    @Test
    @DisplayName("GET /users/{userId} - 존재하지 않는 회원이면 404")
    void getUser_notFound() throws Exception {
        // given
        given(userService.getUser(eq(idOf(999)), any(User.class))).willThrow(new BaseException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/users/{userId}", idOf(999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("GET /users/{userId} - 다른 회원 조회 권한이 없으면 403 USER_ACCESS_DENIED")
    void getUser_accessDenied() throws Exception {
        // given
        given(userService.getUser(eq(idOf(2)), any(User.class))).willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED));

        // when & then
        mockMvc.perform(get("/users/{userId}", idOf(2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /users/{userId} - userId가 UUID 형식이 아니면 400")
    void getUser_invalidUserId() throws Exception {
        mockMvc.perform(get("/users/{userId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ===== PUT /users/{userId} =====

    private static final String VALID_UPDATE_BODY = """
            {
              "email": "new@test.com",
              "password": "newPassword1!",
              "slackId": "new-slack",
              "role": "MASTER"
            }
            """;

    private ResultActions update(UUID userId, String body) throws Exception {
        return mockMvc.perform(put("/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("PUT /users/{userId} - 성공 시 200 과 수정된 회원 정보, 로그인 회원을 요청자로 전달")
    void updateUser_success() throws Exception {
        // given
        UserResponseDto response = new UserResponseDto(idOf(2), "tester", "new@test.com", AffiliationType.COMPANY, AFFILIATION_ID, "new-slack", CREATED_AT);
        given(userService.updateUser(eq(idOf(2)), any(UpdateUserRequestDto.class), same(loginUser))).willReturn(response);

        // when & then
        update(idOf(2), VALID_UPDATE_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 정보를 수정했습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.id").value(idOf(2).toString()))
                .andExpect(jsonPath("$.data.email").value("new@test.com"))
                .andExpect(jsonPath("$.data.affiliationType").value("COMPANY"))
                .andExpect(jsonPath("$.data.affiliationId").value(AFFILIATION_ID.toString()))
                .andExpect(jsonPath("$.data.slackId").value("new-slack"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("PUT /users/{userId} - 권한이 없으면 403 USER_ACCESS_DENIED")
    void updateUser_accessDenied() throws Exception {
        // given
        given(userService.updateUser(eq(idOf(2)), any(UpdateUserRequestDto.class), any(User.class)))
                .willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED));

        // when & then
        update(idOf(2), VALID_UPDATE_BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PUT /users/{userId} - 대상 회원이 없으면 404 USER_NOT_FOUND")
    void updateUser_notFound() throws Exception {
        // given
        given(userService.updateUser(eq(idOf(999)), any(UpdateUserRequestDto.class), any(User.class)))
                .willThrow(new BaseException(ErrorCode.USER_NOT_FOUND));

        // when & then
        update(idOf(999), VALID_UPDATE_BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /users/{userId} - PUT 이므로 필드가 하나라도 빠지면 400 VALIDATION_FAILED")
    void updateUser_missingFields() throws Exception {
        update(idOf(2), """
                { "email": "new@test.com" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("password")))
                .andExpect(jsonPath("$.message").value(containsString("slackId")))
                .andExpect(jsonPath("$.message").value(containsString("role")));
        then(userService).should(never()).updateUser(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /users/{userId} - 소속을 보내면 무시하지 않고 400 VALIDATION_FAILED")
    void updateUser_affiliationNotAllowed() throws Exception {
        String body = VALID_UPDATE_BODY.replace("\"role\": \"MASTER\"",
                "\"role\": \"MASTER\", \"affiliationType\": \"HUB\", \"affiliationId\": \"00000000-0000-0000-0000-000000002000\"");

        update(idOf(2), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("affiliationType: 소속은 변경할 수 없습니다.")))
                .andExpect(jsonPath("$.message").value(containsString("affiliationId: 소속은 변경할 수 없습니다.")));
        then(userService).should(never()).updateUser(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /users/{userId} - 없는 role 값이면 400 INVALID_INPUT")
    void updateUser_invalidRole() throws Exception {
        update(idOf(2), VALID_UPDATE_BODY.replace("\"MASTER\"", "\"ADMIN\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).updateUser(any(), any(), any());
    }

    // ===== GET /users?page=&size= =====

    @Test
    @DisplayName("GET /users - 성공 시 200 과 PageResponse 형식, 로그인 회원을 요청자로 전달")
    void getAllUsers_success() throws Exception {
        // given
        PageResponse<UserResponseDto> response = new PageResponse<>(
                List.of(new UserResponseDto(idOf(1), "master", "master@test.com", AffiliationType.COMPANY, AFFILIATION_ID, "slack-master", CREATED_AT),
                        new UserResponseDto(idOf(2), "tester", "tester@test.com", AffiliationType.COMPANY, AFFILIATION_ID, "slack-1", CREATED_AT)),
                1, 2, 5, 3);
        given(userService.getAllUsers(same(loginUser), eq(1), eq(2))).willReturn(response);

        // when & then
        mockMvc.perform(get("/users").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 목록을 조회했습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(idOf(1).toString()))
                .andExpect(jsonPath("$.data.content[1].id").value(idOf(2).toString()))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    @DisplayName("GET /users - page, size 를 생략하면 기본값 0, 10")
    void getAllUsers_defaultParams() throws Exception {
        // given
        given(userService.getAllUsers(same(loginUser), eq(0), eq(10)))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        // when & then
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
        then(userService).should().getAllUsers(same(loginUser), eq(0), eq(10));
    }

    @Test
    @DisplayName("GET /users - USER 권한이면 403 USER_ACCESS_DENIED")
    void getAllUsers_accessDenied() throws Exception {
        // given
        given(userService.getAllUsers(any(User.class), eq(0), eq(10)))
                .willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED));

        // when & then
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /users - page 가 음수면 400 VALIDATION_FAILED")
    void getAllUsers_negativePage() throws Exception {
        mockMvc.perform(get("/users").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("page")));
        then(userService).should(never()).getAllUsers(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /users - size 가 0 이거나 100 초과면 400 VALIDATION_FAILED")
    void getAllUsers_invalidSize() throws Exception {
        mockMvc.perform(get("/users").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("size")));
        mockMvc.perform(get("/users").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("size")));
        then(userService).should(never()).getAllUsers(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /users - size 100 은 허용")
    void getAllUsers_maxSize() throws Exception {
        // given
        given(userService.getAllUsers(same(loginUser), eq(0), eq(100)))
                .willReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        // when & then
        mockMvc.perform(get("/users").param("size", "100"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users - page 가 숫자가 아니면 400 INVALID_INPUT")
    void getAllUsers_nonNumericPage() throws Exception {
        mockMvc.perform(get("/users").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).getAllUsers(any(), anyInt(), anyInt());
    }

    // ===== DELETE /users/{userId} =====

    @Test
    @DisplayName("DELETE /users/{userId} - 성공 시 204 No Content, 로그인 회원을 요청자로 전달")
    void deleteUser_success() throws Exception {
        mockMvc.perform(delete("/users/{userId}", idOf(2)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        then(userService).should().deleteUser(eq(idOf(2)), same(loginUser));
    }

    @Test
    @DisplayName("DELETE /users/{userId} - 권한이 없으면 403 USER_ACCESS_DENIED")
    void deleteUser_accessDenied() throws Exception {
        // given
        willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED))
                .given(userService).deleteUser(eq(idOf(2)), any(User.class));

        // when & then
        mockMvc.perform(delete("/users/{userId}", idOf(2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("DELETE /users/{userId} - 대상 회원이 없으면 404 USER_NOT_FOUND")
    void deleteUser_notFound() throws Exception {
        // given
        willThrow(new BaseException(ErrorCode.USER_NOT_FOUND))
                .given(userService).deleteUser(eq(idOf(999)), any(User.class));

        // when & then
        mockMvc.perform(delete("/users/{userId}", idOf(999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /users/{userId} - userId가 UUID 형식이 아니면 400 INVALID_INPUT")
    void deleteUser_invalidUserId() throws Exception {
        mockMvc.perform(delete("/users/{userId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).deleteUser(any(), any());
    }

    // ===== 응답 형식 공통 =====

    @Test
    @DisplayName("GET /users/user-info - 성공 시 200 과 ApiResponse 로 감싼 로그인 회원 정보")
    void getUserInfo_success() throws Exception {
        mockMvc.perform(get("/users/user-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("로그인 회원 정보를 조회했습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.username").value("master"))
                .andExpect(jsonPath("$.data.admin").value(true));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500 INTERNAL_SERVER_ERROR, 내부 예외 메시지는 노출하지 않음")
    void unexpectedException_returns500WithoutInternalMessage() throws Exception {
        given(userService.getUser(eq(idOf(1)), any(User.class)))
                .willThrow(new IllegalStateException("SELECT * FROM users -- internal detail"));

        mockMvc.perform(get("/users/{userId}", idOf(1)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("internal detail"))));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405 와 UNSUPPORTED_REQUEST (400 으로 바꾸지 않음)")
    void methodNotAllowed_returns405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/users/{userId}", idOf(1)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNSUPPORTED_REQUEST.getMessage()));
    }
}
