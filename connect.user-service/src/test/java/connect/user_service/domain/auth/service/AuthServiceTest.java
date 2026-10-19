package connect.user_service.domain.auth.service;

import connect.user_service.global.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    private static final long ACCESS_EXPIRATION = 3_600_000L;

    private AuthService authService;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // 실제 키 대신 테스트용 임의 키 사용 (AuthService, JwtUtil 이 같은 키를 공유)
        byte[] keyBytes = new byte[64];
        new SecureRandom().nextBytes(keyBytes);
        String secretKey = Encoders.BASE64URL.encode(keyBytes);

        authService = new AuthService(secretKey);
        ReflectionTestUtils.setField(authService, "issuer", "user-service");
        ReflectionTestUtils.setField(authService, "accessExpiration", ACCESS_EXPIRATION);

        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", secretKey);
        jwtUtil.init();
    }

    @Test
    @DisplayName("발급한 토큰을 JwtUtil 이 검증할 수 있음 (키, 형식 호환)")
    void createAccessToken_validatedByJwtUtil() {
        // when
        String token = authService.createAccessToken("tester");

        // then
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("토큰에 username, issuer, 만료 시간이 들어가고 password 는 없음")
    void createAccessToken_claims() {
        // when
        Claims claims = jwtUtil.getUserInfoFromToken(authService.createAccessToken("tester"));

        // then
        assertThat(claims.get(JwtUtil.USERNAME_KEY, String.class)).isEqualTo("tester");
        assertThat(claims.getIssuer()).isEqualTo("user-service");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isBetween(ACCESS_EXPIRATION - 1_000, ACCESS_EXPIRATION + 1_000);
        assertThat(claims).doesNotContainKey("password");
    }
}
