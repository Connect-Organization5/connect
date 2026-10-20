package connect.user_service.global.security;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final long ACCESS_EXPIRATION = 3_600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = createJwtUtil(randomKey());
    }

    // 실제 키 대신 테스트용 임의 키 사용
    private String randomKey() {
        byte[] keyBytes = new byte[64];
        new SecureRandom().nextBytes(keyBytes);
        return Encoders.BASE64URL.encode(keyBytes);
    }

    private JwtUtil createJwtUtil(String secretKey) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secretKey", secretKey);
        ReflectionTestUtils.setField(util, "accessExpiration", ACCESS_EXPIRATION);
        ReflectionTestUtils.setField(util, "issuer", "user-service");
        util.init();
        return util;
    }

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");

    private User testUser(UserRole role) {
        User user = new User("tester", "encodedPw", "tester@test.com", role, AffiliationType.HUB, AFFILIATION_ID, "slack-1");
        user.setId(USER_ID);
        return user;
    }

    @Test
    @DisplayName("토큰에 Gateway 가 전달할 userId, role, affiliationType, affiliationId 가 문자열로 들어감")
    void createAccessToken_userClaims() {
        // when
        Claims claims = jwtUtil.getUserInfoFromToken(jwtUtil.createAccessToken(testUser(UserRole.USER)));

        // then
        assertThat(claims.get(JwtUtil.USERNAME_KEY, String.class)).isEqualTo("tester");
        assertThat(claims.get(JwtUtil.USER_ID_KEY, String.class)).isEqualTo(USER_ID.toString());
        assertThat(claims.get(JwtUtil.ROLE_KEY, String.class)).isEqualTo("USER");
        assertThat(claims.get(JwtUtil.AFFILIATION_TYPE_KEY, String.class)).isEqualTo("HUB");
        assertThat(claims.get(JwtUtil.AFFILIATION_ID_KEY, String.class)).isEqualTo(AFFILIATION_ID.toString());
    }

    @Test
    @DisplayName("MASTER 회원이면 role claim 이 MASTER (ROLE_ 접두사 없음)")
    void createAccessToken_masterRole() {
        // when
        Claims claims = jwtUtil.getUserInfoFromToken(jwtUtil.createAccessToken(testUser(UserRole.MASTER)));

        // then
        assertThat(claims.get(JwtUtil.ROLE_KEY, String.class)).isEqualTo("MASTER");
    }

    @Test
    @DisplayName("비밀번호, 이메일, Slack ID 는 토큰에 넣지 않음")
    void createAccessToken_noPersonalInfo() {
        // when
        Claims claims = jwtUtil.getUserInfoFromToken(jwtUtil.createAccessToken(testUser(UserRole.USER)));

        // then
        assertThat(claims).doesNotContainKeys("password", "email", "slackId");
        assertThat(claims.values()).doesNotContain("encodedPw", "tester@test.com", "slack-1");
    }

    @Test
    @DisplayName("발급한 토큰을 같은 키의 JwtUtil 이 검증할 수 있음")
    void createAccessToken_validated() {
        // when
        String token = jwtUtil.createAccessToken(testUser(UserRole.USER));

        // then
        assertThat(token).doesNotStartWith(JwtUtil.BEARER_PREFIX);
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("토큰에 username, issuer, 만료 시간이 들어가고 password 는 없음")
    void createAccessToken_claims() {
        // when
        Claims claims = jwtUtil.getUserInfoFromToken(jwtUtil.createAccessToken(testUser(UserRole.USER)));

        // then
        assertThat(claims.get(JwtUtil.USERNAME_KEY, String.class)).isEqualTo("tester");
        assertThat(claims.getIssuer()).isEqualTo("user-service");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isBetween(ACCESS_EXPIRATION - 1_000, ACCESS_EXPIRATION + 1_000);
        assertThat(claims).doesNotContainKey("password");
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 검증 실패")
    void validateToken_otherKey() {
        // given
        String token = createJwtUtil(randomKey()).createAccessToken(testUser(UserRole.USER));

        // then
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Authorization 헤더에서 Bearer 뒤의 토큰만 추출")
    void getJwtFromHeader() {
        // given
        MockHttpServletRequest withBearer = new MockHttpServletRequest();
        withBearer.addHeader(JwtUtil.AUTHORIZATION_HEADER, JwtUtil.BEARER_PREFIX + "token-value");
        MockHttpServletRequest withoutBearer = new MockHttpServletRequest();
        withoutBearer.addHeader(JwtUtil.AUTHORIZATION_HEADER, "token-value");

        // then
        assertThat(jwtUtil.getJwtFromHeader(withBearer)).isEqualTo("token-value");
        assertThat(jwtUtil.getJwtFromHeader(withoutBearer)).isNull();
        assertThat(jwtUtil.getJwtFromHeader(new MockHttpServletRequest())).isNull();
    }
}
