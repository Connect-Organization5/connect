package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UserResponse;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.dto.PageResponse;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private SignUpRequestDto signUpRequest() {
        SignUpRequestDto request = new SignUpRequestDto();
        request.setUsername("tester");
        request.setPassword("password1!");
        request.setEmail("tester@test.com");
        request.setAffiliationType("COMPANY");
        request.setAffiliationId(1L);
        request.setSlackId("slack-1");
        return request;
    }

    private LoginRequestDto loginRequest(String username, String password) {
        LoginRequestDto request = new LoginRequestDto();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    @Test
    @DisplayName("로그인 성공 - DB 에서 찾은 회원 반환")
    void login_success() {
        // given
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, "COMPANY", 1L, "slack-1");
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1!", "encodedPw")).willReturn(true);

        // when
        User result = userService.login(loginRequest("tester", "password1!"));

        // then
        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("로그인 실패 - 없는 아이디면 INVALID_CREDENTIALS")
    void login_userNotFound() {
        // given
        given(userRepository.findByUsername("nobody")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.login(loginRequest("nobody", "password1!")))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호가 틀리면 INVALID_CREDENTIALS (없는 아이디와 같은 에러)")
    void login_wrongPassword() {
        // given
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, "COMPANY", 1L, "slack-1");
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "encodedPw")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.login(loginRequest("tester", "wrong-password")))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("회원가입 성공 - 암호화된 비밀번호와 USER 권한으로 저장")
    void signup_success() {
        // given
        SignUpRequestDto request = signUpRequest();
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(false);
        given(passwordEncoder.encode("password1!")).willReturn("encodedPw");

        // when
        userService.signup(request);

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("tester");
        assertThat(saved.getPassword()).isEqualTo("encodedPw");
        assertThat(saved.getEmail()).isEqualTo("tester@test.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getAffiliationType()).isEqualTo("COMPANY");
        assertThat(saved.getAffiliationId()).isEqualTo(1L);
        assertThat(saved.getSlackId()).isEqualTo("slack-1");
    }

    @Test
    @DisplayName("회원가입 실패 - username 중복")
    void signup_duplicateUsername() {
        // given
        given(userRepository.existsByUsername("tester")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signup(signUpRequest()))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
        then(userRepository).should(never()).save(any());
        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("회원가입 실패 - email 중복")
    void signup_duplicateEmail() {
        // given
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signup(signUpRequest()))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입 실패 - slackId 중복")
    void signup_duplicateSlackId() {
        // given
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signup(signUpRequest()))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SLACK_ID_ALREADY_EXISTS);
        then(userRepository).should(never()).save(any());
    }

    // ===== 회원 단건 조회 =====

    @Test
    @DisplayName("회원 단건 조회 성공 - USER 는 본인 정보 조회 가능")
    void getUser_userSelf() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);
        User self = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(1L)).willReturn(Optional.of(self));

        // when
        UserResponse response = userService.getUser(1L, requester);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("user1");
        assertThat(response.email()).isEqualTo("user1@test.com");
        assertThat(response.affiliationType()).isEqualTo("COMPANY");
        assertThat(response.affiliationId()).isEqualTo(AFFILIATION_ID);
        assertThat(response.slackId()).isEqualTo("slack-1");
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - USER 가 다른 회원을 조회하면 USER_ACCESS_DENIED, 대상 회원은 조회하지 않음")
    void getUser_userOther() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.getUser(2L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - USER 가 없는 회원을 조회해도 404 가 아닌 USER_ACCESS_DENIED")
    void getUser_userOtherNotExisting() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.getUser(999L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 단건 조회 성공 - MASTER 는 같은 소속 회원 조회 가능")
    void getUser_masterSameAffiliation() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", Long.valueOf(AFFILIATION_ID));
        User target = userOf(2L, UserRole.USER, "COMPANY", Long.valueOf(AFFILIATION_ID));
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when
        UserResponse response = userService.getUser(2L, requester);

        // then
        assertThat(response.id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("회원 단건 조회 성공 - MASTER 는 본인 정보 조회 가능")
    void getUser_masterSelf() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User self = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(1L)).willReturn(Optional.of(self));

        // when
        UserResponse response = userService.getUser(1L, requester);

        // then
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - MASTER 라도 소속 유형이 다르면 USER_ACCESS_DENIED")
    void getUser_masterDifferentAffiliationType() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "HUB", AFFILIATION_ID);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.getUser(2L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - MASTER 라도 소속 ID 가 다르면 USER_ACCESS_DENIED")
    void getUser_masterDifferentAffiliationId() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "COMPANY", AFFILIATION_ID + 1);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.getUser(2L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - MASTER 가 없는 회원을 조회하면 USER_NOT_FOUND")
    void getUser_masterNotFound() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUser(999L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===== 회원 정보 수정 =====

    // affiliationId 는 1000 처럼 Long 캐시(-128~127) 밖의 값을 사용해서 == 비교 버그를 잡음
    private static final long AFFILIATION_ID = 1000L;

    private User userOf(Long id, UserRole role, String affiliationType, Long affiliationId) {
        User user = new User("user" + id, "encodedPw", "user" + id + "@test.com", role, affiliationType, affiliationId, "slack-" + id);
        user.setId(id);
        return user;
    }

    private UpdateUserRequestDto updateRequest() {
        UpdateUserRequestDto request = new UpdateUserRequestDto();
        request.setEmail("new@test.com");
        request.setPassword("newPassword1!");
        request.setSlackId("new-slack");
        request.setRole(UserRole.MASTER);
        request.setAffiliationType("HUB");
        request.setAffiliationId(2000L);
        return request;
    }

    @Test
    @DisplayName("회원 수정 성공 - MASTER 이고 소속이 같으면 6개 필드 모두 수정")
    void updateUser_success() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", Long.valueOf(AFFILIATION_ID));
        User target = userOf(2L, UserRole.USER, "COMPANY", Long.valueOf(AFFILIATION_ID));
        given(userRepository.findById(2L)).willReturn(Optional.of(target));
        given(userRepository.existsByEmailAndIdNot("new@test.com", 2L)).willReturn(false);
        given(userRepository.existsBySlackIdAndIdNot("new-slack", 2L)).willReturn(false);
        given(passwordEncoder.encode("newPassword1!")).willReturn("newEncodedPw");

        // when
        UserResponse response = userService.updateUser(2L, updateRequest(), requester);

        // then
        assertThat(target.getEmail()).isEqualTo("new@test.com");
        assertThat(target.getPassword()).isEqualTo("newEncodedPw");
        assertThat(target.getSlackId()).isEqualTo("new-slack");
        assertThat(target.getRole()).isEqualTo(UserRole.MASTER);
        assertThat(target.getAffiliationType()).isEqualTo("HUB");
        assertThat(target.getAffiliationId()).isEqualTo(2000L);
        assertThat(target.getUsername()).isEqualTo("user2");
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.email()).isEqualTo("new@test.com");
    }

    @Test
    @DisplayName("회원 수정 성공 - MASTER 는 본인 정보 수정 가능")
    void updateUser_masterSelf() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User self = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(1L)).willReturn(Optional.of(self));
        given(passwordEncoder.encode("newPassword1!")).willReturn("newEncodedPw");

        // when
        userService.updateUser(1L, updateRequest(), requester);

        // then
        assertThat(self.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    @DisplayName("회원 수정 실패 - USER 권한이면 USER_ACCESS_DENIED, 대상 회원은 조회하지 않음")
    void updateUser_userRole() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(2L, updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 수정 실패 - USER 는 본인 정보도 수정 불가")
    void updateUser_userSelf() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(1L, updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 수정 실패 - 소속 유형이 다르면 USER_ACCESS_DENIED")
    void updateUser_differentAffiliationType() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "HUB", AFFILIATION_ID);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.updateUser(2L, updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        assertThat(target.getEmail()).isEqualTo("user2@test.com");
    }

    @Test
    @DisplayName("회원 수정 실패 - 소속 ID 가 다르면 USER_ACCESS_DENIED")
    void updateUser_differentAffiliationId() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "COMPANY", AFFILIATION_ID + 1);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.updateUser(2L, updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        assertThat(target.getEmail()).isEqualTo("user2@test.com");
    }

    @Test
    @DisplayName("회원 수정 실패 - 대상 회원이 없으면 USER_NOT_FOUND")
    void updateUser_notFound() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateUser(999L, updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원 수정 실패 - 다른 회원과 email 중복이면 EMAIL_ALREADY_EXISTS")
    void updateUser_duplicateEmail() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));
        given(userRepository.existsByEmailAndIdNot("new@test.com", 2L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(2L, updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("회원 수정 실패 - 다른 회원과 slackId 중복이면 SLACK_ID_ALREADY_EXISTS")
    void updateUser_duplicateSlackId() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));
        given(userRepository.existsByEmailAndIdNot("new@test.com", 2L)).willReturn(false);
        given(userRepository.existsBySlackIdAndIdNot("new-slack", 2L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(2L, updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SLACK_ID_ALREADY_EXISTS);
        then(passwordEncoder).should(never()).encode(any());
    }

    // ===== 회원 목록 조회 =====

    @Test
    @DisplayName("회원 목록 조회 성공 - 요청자 소속, MASTER 우선 정렬 기준, page/size 로 조회")
    void getAllUsers_success() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User member = userOf(2L, UserRole.USER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findAllByAffiliation(eq("COMPANY"), eq(AFFILIATION_ID), eq(UserRole.MASTER), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(requester, member), invocation.getArgument(3), 12));

        // when
        PageResponse<UserResponse> response = userService.getAllUsers(requester, 1, 10);

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(userRepository).should().findAllByAffiliation(eq("COMPANY"), eq(AFFILIATION_ID), eq(UserRole.MASTER), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);

        assertThat(response.content()).extracting(UserResponse::id).containsExactly(1L, 2L);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("회원 목록 조회 - 결과가 없으면 빈 목록")
    void getAllUsers_empty() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findAllByAffiliation(eq("COMPANY"), eq(AFFILIATION_ID), eq(UserRole.MASTER), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<User>(List.of(), invocation.getArgument(3), 0));

        // when
        PageResponse<UserResponse> response = userService.getAllUsers(requester, 0, 10);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    @DisplayName("회원 목록 조회 실패 - USER 권한이면 USER_ACCESS_DENIED, Repository 는 호출하지 않음")
    void getAllUsers_userRole() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.getAllUsers(requester, 0, 10))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findAllByAffiliation(any(), any(), any(), any());
    }

    // ===== 회원 삭제 (논리적 삭제) =====

    @Test
    @DisplayName("회원 삭제 성공 - MASTER 이고 소속이 같으면 요청자 id 로 논리적 삭제")
    void deleteUser_success() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", Long.valueOf(AFFILIATION_ID));
        User target = userOf(2L, UserRole.USER, "COMPANY", Long.valueOf(AFFILIATION_ID));
        given(userRepository.findById(2L)).willReturn(Optional.of(target));
        given(userRepository.softDeleteById(eq(2L), eq(1L), any(LocalDateTime.class))).willReturn(1);

        // when
        userService.deleteUser(2L, requester);

        // then
        then(userRepository).should().softDeleteById(eq(2L), eq(1L), any(LocalDateTime.class));
        then(userRepository).should(never()).save(any());
        then(userRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("회원 삭제 성공 - MASTER 는 본인 삭제 가능")
    void deleteUser_masterSelf() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User self = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(1L)).willReturn(Optional.of(self));
        given(userRepository.softDeleteById(eq(1L), eq(1L), any(LocalDateTime.class))).willReturn(1);

        // when
        userService.deleteUser(1L, requester);

        // then
        then(userRepository).should().softDeleteById(eq(1L), eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("회원 삭제 실패 - USER 권한이면 USER_ACCESS_DENIED, 대상 회원은 조회하지 않음")
    void deleteUser_userRole() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(2L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - USER 는 본인도 삭제 불가")
    void deleteUser_userSelf() {
        // given
        User requester = userOf(1L, UserRole.USER, "COMPANY", AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(1L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 소속 유형이 다르면 USER_ACCESS_DENIED")
    void deleteUser_differentAffiliationType() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "HUB", AFFILIATION_ID);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(2L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 소속 ID 가 다르면 USER_ACCESS_DENIED")
    void deleteUser_differentAffiliationId() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "COMPANY", AFFILIATION_ID + 1);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(2L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 대상 회원이 없거나 이미 삭제됐으면 USER_NOT_FOUND")
    void deleteUser_notFound() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(999L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 조회 후 다른 요청이 먼저 삭제해서 반영된 행이 0 이면 USER_NOT_FOUND")
    void deleteUser_concurrentlyDeleted() {
        // given
        User requester = userOf(1L, UserRole.MASTER, "COMPANY", AFFILIATION_ID);
        User target = userOf(2L, UserRole.USER, "COMPANY", AFFILIATION_ID);
        given(userRepository.findById(2L)).willReturn(Optional.of(target));
        given(userRepository.softDeleteById(eq(2L), eq(1L), any(LocalDateTime.class))).willReturn(0);

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(2L, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
