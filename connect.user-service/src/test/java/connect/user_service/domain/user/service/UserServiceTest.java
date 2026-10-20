package connect.user_service.domain.user.service;

import connect.user_service.domain.client.clientinterface.CompanyClient;
import connect.user_service.domain.client.clientinterface.HubClient;
import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.CompanyInfo;
import connect.user_service.domain.user.dto.response.HubInfo;
import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.domain.user.entity.AffiliationType;
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
import java.util.UUID;

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

    @Mock
    private CompanyClient companyClient;

    @Mock
    private HubClient hubClient;

    @InjectMocks
    private UserService userService;

    private SignUpRequestDto signUpRequest() {
        SignUpRequestDto request = new SignUpRequestDto();
        request.setUsername("tester");
        request.setPassword("password1!");
        request.setEmail("tester@test.com");
        request.setAffiliationType(AffiliationType.COMPANY);
        request.setAffiliationId(AFFILIATION_ID);
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
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID, "slack-1");
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
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID, "slack-1");
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "encodedPw")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.login(loginRequest("tester", "wrong-password")))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("회원가입 성공 - 로그인 없이 요청한 소속으로 암호화된 비밀번호와 USER 권한으로 저장")
    void signup_success() {
        // given
        SignUpRequestDto request = signUpRequest();
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(false);
        given(companyClient.getCompany(AFFILIATION_ID)).willReturn(companyInfo());
        given(passwordEncoder.encode("password1!")).willReturn("encodedPw");

        // when
        UserResponseDto response = userService.signup(request);

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("tester");
        assertThat(saved.getPassword()).isEqualTo("encodedPw");
        assertThat(saved.getEmail()).isEqualTo("tester@test.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getAffiliationType()).isEqualTo(AffiliationType.COMPANY);
        assertThat(saved.getAffiliationId()).isEqualTo(AFFILIATION_ID);
        assertThat(saved.getSlackId()).isEqualTo("slack-1");
        assertThat(response.username()).isEqualTo("tester");

        // COMPANY 소속이면 Company Service 에만 존재 여부를 확인
        then(companyClient).should().getCompany(AFFILIATION_ID);
        then(hubClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원가입 성공 - HUB 소속이면 Hub Service 에만 존재 여부를 확인하고 저장")
    void signup_hubAffiliation() {
        // given
        SignUpRequestDto request = signUpRequest();
        request.setAffiliationType(AffiliationType.HUB);
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(false);
        given(hubClient.getHub(AFFILIATION_ID)).willReturn(hubInfo());
        given(passwordEncoder.encode("password1!")).willReturn("encodedPw");

        // when
        userService.signup(request);

        // then
        then(hubClient).should().getHub(AFFILIATION_ID);
        then(companyClient).shouldHaveNoInteractions();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAffiliationType()).isEqualTo(AffiliationType.HUB);
        assertThat(captor.getValue().getAffiliationId()).isEqualTo(AFFILIATION_ID);
    }

    @Test
    @DisplayName("회원가입 실패 - COMPANY 소속인데 업체가 없으면 COMPANY_NOT_FOUND, 저장하지 않음")
    void signup_companyNotFound() {
        // given
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(false);
        given(companyClient.getCompany(AFFILIATION_ID)).willThrow(new BaseException(ErrorCode.COMPANY_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> userService.signup(signUpRequest()))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
        then(hubClient).shouldHaveNoInteractions();
        then(userRepository).should(never()).save(any());
        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("회원가입 실패 - HUB 소속인데 허브가 없으면 HUB_NOT_FOUND, 저장하지 않음")
    void signup_hubNotFound() {
        // given
        SignUpRequestDto request = signUpRequest();
        request.setAffiliationType(AffiliationType.HUB);
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(false);
        given(hubClient.getHub(AFFILIATION_ID)).willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HUB_NOT_FOUND);
        then(companyClient).shouldHaveNoInteractions();
        then(userRepository).should(never()).save(any());
        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("회원가입 실패 - Company Service 가 200 이지만 data 가 null 이면 COMPANY_NOT_FOUND")
    void signup_companyDataNull() {
        // given
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(false);
        given(companyClient.getCompany(AFFILIATION_ID)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> userService.signup(signUpRequest()))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("회원가입 실패 - Hub Service 가 200 이지만 data 가 null 이면 HUB_NOT_FOUND")
    void signup_hubDataNull() {
        // given
        SignUpRequestDto request = signUpRequest();
        request.setAffiliationType(AffiliationType.HUB);
        given(userRepository.existsByUsername("tester")).willReturn(false);
        given(userRepository.existsByEmail("tester@test.com")).willReturn(false);
        given(userRepository.existsBySlackId("slack-1")).willReturn(false);
        given(hubClient.getHub(AFFILIATION_ID)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HUB_NOT_FOUND);
        then(userRepository).should(never()).save(any());
    }

    private CompanyInfo companyInfo() {
        return new CompanyInfo(AFFILIATION_ID, null, idOf(2000), "커넥트상사", "서울특별시 강남구 테헤란로 1");
    }

    private HubInfo hubInfo() {
        return new HubInfo(AFFILIATION_ID, "서울특별시 센터", "서울특별시 송파구 송파대로 55");
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
        // 중복이면 다른 서비스에 소속 조회 요청을 보내지 않음
        then(companyClient).shouldHaveNoInteractions();
        then(hubClient).shouldHaveNoInteractions();
    }

    // ===== 회원 단건 조회 =====

    @Test
    @DisplayName("회원 단건 조회 성공 - USER 는 본인 정보 조회 가능")
    void getUser_userSelf() {
        // given
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        User self = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(1))).willReturn(Optional.of(self));

        // when
        UserResponseDto response = userService.getUser(idOf(1), requester);

        // then
        assertThat(response.id()).isEqualTo(idOf(1));
        assertThat(response.username()).isEqualTo("user1");
        assertThat(response.email()).isEqualTo("user1@test.com");
        assertThat(response.affiliationType()).isEqualTo(AffiliationType.COMPANY);
        assertThat(response.affiliationId()).isEqualTo(AFFILIATION_ID);
        assertThat(response.slackId()).isEqualTo("slack-1");
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - USER 가 다른 회원을 조회하면 USER_ACCESS_DENIED, 대상 회원은 조회하지 않음")
    void getUser_userOther() {
        // given
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.getUser(idOf(2), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - USER 가 없는 회원을 조회해도 404 가 아닌 USER_ACCESS_DENIED")
    void getUser_userOtherNotExisting() {
        // given
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.getUser(idOf(999), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 단건 조회 성공 - MASTER 는 같은 소속 회원 조회 가능")
    void getUser_masterSameAffiliation() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));

        // when
        UserResponseDto response = userService.getUser(idOf(2), requester);

        // then
        assertThat(response.id()).isEqualTo(idOf(2));
    }

    @Test
    @DisplayName("회원 단건 조회 성공 - MASTER 는 본인 정보 조회 가능")
    void getUser_masterSelf() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User self = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(1))).willReturn(Optional.of(self));

        // when
        UserResponseDto response = userService.getUser(idOf(1), requester);

        // then
        assertThat(response.id()).isEqualTo(idOf(1));
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - MASTER 라도 소속 유형이 다르면 USER_ACCESS_DENIED")
    void getUser_masterDifferentAffiliationType() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.HUB, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.getUser(idOf(2), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - MASTER 라도 소속 ID 가 다르면 USER_ACCESS_DENIED")
    void getUser_masterDifferentAffiliationId() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, OTHER_AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.getUser(idOf(2), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("회원 단건 조회 실패 - MASTER 가 없는 회원을 조회하면 USER_NOT_FOUND")
    void getUser_masterNotFound() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(999))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUser(idOf(999), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===== 회원 정보 수정 =====

    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");
    private static final UUID OTHER_AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000002000");

    // 테스트용 고정 UUID (idOf(1) → 00000000-0000-0000-0000-000000000001)
    private static UUID idOf(long no) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(no));
    }

    // no 는 username/email/slackId 구분용 번호, id 는 idOf(no)
    // affiliationId 는 새 UUID 객체로 복사해서 넣어 == 비교 버그를 잡음 (equals 로 비교해야 통과)
    private User userOf(long no, UserRole role, AffiliationType affiliationType, UUID affiliationId) {
        User user = new User("user" + no, "encodedPw", "user" + no + "@test.com", role, affiliationType,
                UUID.fromString(affiliationId.toString()), "slack-" + no);
        user.setId(idOf(no));
        return user;
    }

    private UpdateUserRequestDto updateRequest() {
        UpdateUserRequestDto request = new UpdateUserRequestDto();
        request.setEmail("new@test.com");
        request.setPassword("newPassword1!");
        request.setSlackId("new-slack");
        request.setRole(UserRole.MASTER);
        return request;
    }

    @Test
    @DisplayName("회원 수정 성공 - MASTER 이고 소속이 같으면 email, password, slackId, role 수정, 소속은 그대로")
    void updateUser_success() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));
        given(userRepository.existsByEmailAndIdNot("new@test.com", idOf(2))).willReturn(false);
        given(userRepository.existsBySlackIdAndIdNot("new-slack", idOf(2))).willReturn(false);
        given(passwordEncoder.encode("newPassword1!")).willReturn("newEncodedPw");

        // when
        UserResponseDto response = userService.updateUser(idOf(2), updateRequest(), requester);

        // then
        assertThat(target.getEmail()).isEqualTo("new@test.com");
        assertThat(target.getPassword()).isEqualTo("newEncodedPw");
        assertThat(target.getSlackId()).isEqualTo("new-slack");
        assertThat(target.getRole()).isEqualTo(UserRole.MASTER);
        assertThat(target.getAffiliationType()).isEqualTo(AffiliationType.COMPANY);
        assertThat(target.getAffiliationId()).isEqualTo(AFFILIATION_ID);
        assertThat(target.getUsername()).isEqualTo("user2");
        assertThat(response.id()).isEqualTo(idOf(2));
        assertThat(response.email()).isEqualTo("new@test.com");
    }

    @Test
    @DisplayName("회원 수정 성공 - MASTER 는 본인 정보 수정 가능")
    void updateUser_masterSelf() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User self = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(1))).willReturn(Optional.of(self));
        given(passwordEncoder.encode("newPassword1!")).willReturn("newEncodedPw");

        // when
        userService.updateUser(idOf(1), updateRequest(), requester);

        // then
        assertThat(self.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    @DisplayName("회원 수정 실패 - 소속의 마지막 MASTER 를 USER 로 바꾸면 LAST_MASTER_CANNOT_BE_DEMOTED")
    void updateUser_demoteLastMaster() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User self = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(1))).willReturn(Optional.of(self));
        given(userRepository.findAllByAffiliationAndRoleForUpdate(AffiliationType.COMPANY, AFFILIATION_ID, UserRole.MASTER)).willReturn(List.of(self));
        UpdateUserRequestDto request = updateRequest();
        request.setRole(UserRole.USER);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(1), request, requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LAST_MASTER_CANNOT_BE_DEMOTED);
        assertThat(self.getRole()).isEqualTo(UserRole.MASTER);
        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("회원 수정 성공 - 같은 소속에 다른 MASTER 가 있으면 MASTER 를 USER 로 변경 가능")
    void updateUser_demoteMasterWithOtherMaster() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));
        given(userRepository.findAllByAffiliationAndRoleForUpdate(AffiliationType.COMPANY, AFFILIATION_ID, UserRole.MASTER)).willReturn(List.of(requester, target));
        given(passwordEncoder.encode("newPassword1!")).willReturn("newEncodedPw");
        UpdateUserRequestDto request = updateRequest();
        request.setRole(UserRole.USER);

        // when
        userService.updateUser(idOf(2), request, requester);

        // then
        assertThat(target.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("회원 수정 실패 - USER 권한이면 USER_ACCESS_DENIED, 대상 회원은 조회하지 않음")
    void updateUser_userRole() {
        // given
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(2), updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 수정 실패 - USER 는 본인 정보도 수정 불가")
    void updateUser_userSelf() {
        // given
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(1), updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("회원 수정 실패 - 소속 유형이 다르면 USER_ACCESS_DENIED")
    void updateUser_differentAffiliationType() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.HUB, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(2), updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        assertThat(target.getEmail()).isEqualTo("user2@test.com");
    }

    @Test
    @DisplayName("회원 수정 실패 - 소속 ID 가 다르면 USER_ACCESS_DENIED")
    void updateUser_differentAffiliationId() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, OTHER_AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(2), updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        assertThat(target.getEmail()).isEqualTo("user2@test.com");
    }

    @Test
    @DisplayName("회원 수정 실패 - 대상 회원이 없으면 USER_NOT_FOUND")
    void updateUser_notFound() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(999))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(999), updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원 수정 실패 - 다른 회원과 email 중복이면 EMAIL_ALREADY_EXISTS")
    void updateUser_duplicateEmail() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));
        given(userRepository.existsByEmailAndIdNot("new@test.com", idOf(2))).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(2), updateRequest(), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    @DisplayName("회원 수정 실패 - 다른 회원과 slackId 중복이면 SLACK_ID_ALREADY_EXISTS")
    void updateUser_duplicateSlackId() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));
        given(userRepository.existsByEmailAndIdNot("new@test.com", idOf(2))).willReturn(false);
        given(userRepository.existsBySlackIdAndIdNot("new-slack", idOf(2))).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateUser(idOf(2), updateRequest(), requester))
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
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User member = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findAllByAffiliation(eq(AffiliationType.COMPANY), eq(AFFILIATION_ID), eq(UserRole.MASTER), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(requester, member), invocation.getArgument(3), 12));

        // when
        PageResponse<UserResponseDto> response = userService.getAllUsers(requester, 1, 10);

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        then(userRepository).should().findAllByAffiliation(eq(AffiliationType.COMPANY), eq(AFFILIATION_ID), eq(UserRole.MASTER), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);

        assertThat(response.content()).extracting(UserResponseDto::id).containsExactly(idOf(1), idOf(2));
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("회원 목록 조회 - 결과가 없으면 빈 목록")
    void getAllUsers_empty() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findAllByAffiliation(eq(AffiliationType.COMPANY), eq(AFFILIATION_ID), eq(UserRole.MASTER), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<User>(List.of(), invocation.getArgument(3), 0));

        // when
        PageResponse<UserResponseDto> response = userService.getAllUsers(requester, 0, 10);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    @DisplayName("회원 목록 조회 실패 - USER 권한이면 USER_ACCESS_DENIED, Repository 는 호출하지 않음")
    void getAllUsers_userRole() {
        // given
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);

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
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));
        given(userRepository.softDeleteById(eq(idOf(2)), eq(idOf(1)), any(LocalDateTime.class))).willReturn(1);

        // when
        userService.deleteUser(idOf(2), requester);

        // then
        then(userRepository).should().softDeleteById(eq(idOf(2)), eq(idOf(1)), any(LocalDateTime.class));
        then(userRepository).should(never()).save(any());
        then(userRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("회원 삭제 성공 - 같은 소속에 다른 MASTER 가 있으면 MASTER 는 본인 삭제 가능")
    void deleteUser_masterSelf() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User self = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(1))).willReturn(Optional.of(self));
        given(userRepository.findAllByAffiliationAndRoleForUpdate(AffiliationType.COMPANY, AFFILIATION_ID, UserRole.MASTER))
                .willReturn(List.of(self, userOf(2, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID)));
        given(userRepository.softDeleteById(eq(idOf(1)), eq(idOf(1)), any(LocalDateTime.class))).willReturn(1);

        // when
        userService.deleteUser(idOf(1), requester);

        // then
        then(userRepository).should().softDeleteById(eq(idOf(1)), eq(idOf(1)), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("회원 삭제 실패 - 소속의 마지막 MASTER 면 LAST_MASTER_CANNOT_BE_DELETED")
    void deleteUser_lastMaster() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User self = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(1))).willReturn(Optional.of(self));
        given(userRepository.findAllByAffiliationAndRoleForUpdate(AffiliationType.COMPANY, AFFILIATION_ID, UserRole.MASTER)).willReturn(List.of(self));

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(idOf(1), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LAST_MASTER_CANNOT_BE_DELETED);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 성공 - 대상이 USER 면 MASTER 수를 확인하지 않음")
    void deleteUser_userTargetSkipsMasterCount() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));
        given(userRepository.softDeleteById(eq(idOf(2)), eq(idOf(1)), any(LocalDateTime.class))).willReturn(1);

        // when
        userService.deleteUser(idOf(2), requester);

        // then
        then(userRepository).should(never()).findAllByAffiliationAndRoleForUpdate(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - USER 권한이면 USER_ACCESS_DENIED, 대상 회원은 조회하지 않음")
    void deleteUser_userRole() {
        // given
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(idOf(2), requester))
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
        User requester = userOf(1, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(idOf(1), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 소속 유형이 다르면 USER_ACCESS_DENIED")
    void deleteUser_differentAffiliationType() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.HUB, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(idOf(2), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 소속 ID 가 다르면 USER_ACCESS_DENIED")
    void deleteUser_differentAffiliationId() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, OTHER_AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(idOf(2), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ACCESS_DENIED);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 대상 회원이 없거나 이미 삭제됐으면 USER_NOT_FOUND")
    void deleteUser_notFound() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(999))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(idOf(999), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(userRepository).should(never()).softDeleteById(any(), any(), any());
    }

    @Test
    @DisplayName("회원 삭제 실패 - 조회 후 다른 요청이 먼저 삭제해서 반영된 행이 0 이면 USER_NOT_FOUND")
    void deleteUser_concurrentlyDeleted() {
        // given
        User requester = userOf(1, UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID);
        User target = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(target));
        given(userRepository.softDeleteById(eq(idOf(2)), eq(idOf(1)), any(LocalDateTime.class))).willReturn(0);

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(idOf(2), requester))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===== 다른 서비스에 회원 정보 전달 =====

    @Test
    @DisplayName("회원 정보 전달 성공 - userId 로 조회한 회원 정보를 반환")
    void sendUser_success() {
        // given
        User user = userOf(2, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID);
        given(userRepository.findById(idOf(2))).willReturn(Optional.of(user));

        // when
        UserResponseDto response = userService.sendUser(idOf(2));

        // then
        assertThat(response.id()).isEqualTo(idOf(2));
        assertThat(response.username()).isEqualTo("user2");
        assertThat(response.email()).isEqualTo("user2@test.com");
        assertThat(response.affiliationType()).isEqualTo(AffiliationType.COMPANY);
        assertThat(response.affiliationId()).isEqualTo(AFFILIATION_ID);
        assertThat(response.slackId()).isEqualTo("slack-2");
    }

    @Test
    @DisplayName("회원 정보 전달 실패 - 없거나 삭제된 회원이면 USER_NOT_FOUND")
    void sendUser_notFound() {
        // given
        given(userRepository.findById(idOf(999))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.sendUser(idOf(999)))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
