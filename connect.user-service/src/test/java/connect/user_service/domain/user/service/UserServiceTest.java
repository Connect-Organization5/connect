package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.response.UserResponse;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("회원 단건 조회 성공")
    void getUser_success() {
        // given
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, "COMPANY", 1L, "slack-1");
        user.setId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getUser(1L);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("tester");
        assertThat(response.email()).isEqualTo("tester@test.com");
        assertThat(response.affiliationType()).isEqualTo("COMPANY");
        assertThat(response.affiliationId()).isEqualTo(1L);
        assertThat(response.slackId()).isEqualTo("slack-1");
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - 존재하지 않는 회원")
    void getUser_notFound() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
