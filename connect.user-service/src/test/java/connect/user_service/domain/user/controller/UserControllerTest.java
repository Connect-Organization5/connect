package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UserResponse;
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
        loginUser = new User("master", "encodedPw", "master@test.com", UserRole.MASTER, AffiliationType.COMPANY, 1000L, "slack-master");
        loginUser.setId(1L);
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
        UserResponse response = new UserResponse(1L, "tester", "encodedPw", "tester@test.com", AffiliationType.COMPANY, 1L, "slack-1");
        given(userService.getUser(eq(1L), same(loginUser))).willReturn(response);

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
        given(userService.getUser(eq(999L), any(User.class))).willThrow(new BaseException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/users/{userId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("GET /users/{userId} - 다른 회원 조회 권한이 없으면 403 USER_ACCESS_DENIED")
    void getUser_accessDenied() throws Exception {
        // given
        given(userService.getUser(eq(2L), any(User.class))).willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED));

        // when & then
        mockMvc.perform(get("/users/{userId}", 2L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /users/{userId} - userId가 숫자가 아니면 400")
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
              "role": "MASTER",
              "affiliationType": "HUB",
              "affiliationId": 2000
            }
            """;

    private ResultActions update(Long userId, String body) throws Exception {
        return mockMvc.perform(put("/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("PUT /users/{userId} - 성공 시 200 과 수정된 회원 정보, 로그인 회원을 요청자로 전달")
    void updateUser_success() throws Exception {
        // given
        UserResponse response = new UserResponse(2L, "tester", "newEncodedPw", "new@test.com", AffiliationType.HUB, 2000L, "new-slack");
        given(userService.updateUser(eq(2L), any(UpdateUserRequestDto.class), same(loginUser))).willReturn(response);

        // when & then
        update(2L, VALID_UPDATE_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.email").value("new@test.com"))
                .andExpect(jsonPath("$.affiliationType").value("HUB"))
                .andExpect(jsonPath("$.affiliationId").value(2000L))
                .andExpect(jsonPath("$.slackId").value("new-slack"));
    }

    @Test
    @DisplayName("PUT /users/{userId} - 권한이 없으면 403 USER_ACCESS_DENIED")
    void updateUser_accessDenied() throws Exception {
        // given
        given(userService.updateUser(eq(2L), any(UpdateUserRequestDto.class), any(User.class)))
                .willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED));

        // when & then
        update(2L, VALID_UPDATE_BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PUT /users/{userId} - 대상 회원이 없으면 404 USER_NOT_FOUND")
    void updateUser_notFound() throws Exception {
        // given
        given(userService.updateUser(eq(999L), any(UpdateUserRequestDto.class), any(User.class)))
                .willThrow(new BaseException(ErrorCode.USER_NOT_FOUND));

        // when & then
        update(999L, VALID_UPDATE_BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /users/{userId} - PUT 이므로 필드가 하나라도 빠지면 400 VALIDATION_FAILED")
    void updateUser_missingFields() throws Exception {
        update(2L, """
                { "email": "new@test.com" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("password")))
                .andExpect(jsonPath("$.message").value(containsString("slackId")))
                .andExpect(jsonPath("$.message").value(containsString("role")))
                .andExpect(jsonPath("$.message").value(containsString("affiliationType")))
                .andExpect(jsonPath("$.message").value(containsString("affiliationId")));
        then(userService).should(never()).updateUser(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /users/{userId} - 없는 role 값이면 400 INVALID_INPUT")
    void updateUser_invalidRole() throws Exception {
        update(2L, VALID_UPDATE_BODY.replace("\"MASTER\"", "\"ADMIN\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).updateUser(any(), any(), any());
    }

    // ===== GET /users?page=&size= =====

    @Test
    @DisplayName("GET /users - 성공 시 200 과 PageResponse 형식, 로그인 회원을 요청자로 전달")
    void getAllUsers_success() throws Exception {
        // given
        PageResponse<UserResponse> response = new PageResponse<>(
                List.of(new UserResponse(1L, "master", "encodedPw", "master@test.com", AffiliationType.COMPANY, 1000L, "slack-master"),
                        new UserResponse(2L, "tester", "encodedPw", "tester@test.com", AffiliationType.COMPANY, 1000L, "slack-1")),
                1, 2, 5, 3);
        given(userService.getAllUsers(same(loginUser), eq(1), eq(2))).willReturn(response);

        // when & then
        mockMvc.perform(get("/users").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[1].id").value(2L))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
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
                .andExpect(jsonPath("$.content.length()").value(0));
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
        mockMvc.perform(delete("/users/{userId}", 2L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        then(userService).should().deleteUser(eq(2L), same(loginUser));
    }

    @Test
    @DisplayName("DELETE /users/{userId} - 권한이 없으면 403 USER_ACCESS_DENIED")
    void deleteUser_accessDenied() throws Exception {
        // given
        willThrow(new BaseException(ErrorCode.USER_ACCESS_DENIED))
                .given(userService).deleteUser(eq(2L), any(User.class));

        // when & then
        mockMvc.perform(delete("/users/{userId}", 2L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("DELETE /users/{userId} - 대상 회원이 없으면 404 USER_NOT_FOUND")
    void deleteUser_notFound() throws Exception {
        // given
        willThrow(new BaseException(ErrorCode.USER_NOT_FOUND))
                .given(userService).deleteUser(eq(999L), any(User.class));

        // when & then
        mockMvc.perform(delete("/users/{userId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /users/{userId} - userId가 숫자가 아니면 400 INVALID_INPUT")
    void deleteUser_invalidUserId() throws Exception {
        mockMvc.perform(delete("/users/{userId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        then(userService).should(never()).deleteUser(any(), any());
    }
}
