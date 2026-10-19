package connect.user_service.global.security;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.global.exception.ErrorCode;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthorizationFilterTest {

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    private String secretKey;
    private JwtAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[64];
        new SecureRandom().nextBytes(keyBytes);
        secretKey = Encoders.BASE64URL.encode(keyBytes);

        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", secretKey);
        jwtUtil.init();

        filter = new JwtAuthorizationFilter(jwtUtil, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 실제 로그인과 같은 JwtUtil.createAccessToken 으로 토큰 발급 (키, 만료 시간만 지정)
    private String createToken(String signingKey, String username, long expirationMillis) {
        JwtUtil issuer = new JwtUtil();
        ReflectionTestUtils.setField(issuer, "secretKey", signingKey);
        ReflectionTestUtils.setField(issuer, "accessExpiration", expirationMillis);
        ReflectionTestUtils.setField(issuer, "issuer", "user-service");
        issuer.init();
        User user = new User(username, "encodedPw", username + "@test.com", UserRole.USER, AffiliationType.COMPANY, UUID.fromString("00000000-0000-0000-0000-000000001000"), "slack-" + username);
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return issuer.createAccessToken(user);
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        request.addHeader(JwtUtil.AUTHORIZATION_HEADER, JwtUtil.BEARER_PREFIX + token);
        return request;
    }

    @Test
    @DisplayName("JwtUtil 이 발급한 유효한 토큰이면 SecurityContext 에 인증 정보 저장")
    void validToken_setsAuthentication() throws Exception {
        // given
        User user = new User("tester", "encodedPw", "tester@test.com", UserRole.USER, AffiliationType.COMPANY, UUID.fromString("00000000-0000-0000-0000-000000001000"), "slack-1");
        given(userDetailsService.loadUserByUsername("tester")).willReturn(new UserDetailsImpl(user));
        MockHttpServletRequest request = requestWithToken(createToken(secretKey, "tester", 60_000));
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((UserDetailsImpl) authentication.getPrincipal()).getUsername()).isEqualTo("tester");
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(CustomAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰이면 인증 없이 다음 필터로 넘기고 INVALID_TOKEN 표시")
    void invalidSignature_marksInvalidToken() throws Exception {
        // given
        byte[] otherKey = new byte[64];
        new SecureRandom().nextBytes(otherKey);
        MockHttpServletRequest request = requestWithToken(createToken(Encoders.BASE64URL.encode(otherKey), "tester", 60_000));
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(CustomAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE)).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("만료된 토큰이면 INVALID_TOKEN 표시")
    void expiredToken_marksInvalidToken() throws Exception {
        // given
        MockHttpServletRequest request = requestWithToken(createToken(secretKey, "tester", -60_000));
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(CustomAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE)).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("토큰이 없으면 아무 표시 없이 다음 필터로 넘김")
    void noToken_passesThrough() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(CustomAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE)).isNull();
    }
}
