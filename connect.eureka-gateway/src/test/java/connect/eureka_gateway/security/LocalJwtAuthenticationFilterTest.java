package connect.eureka_gateway.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalJwtAuthenticationFilterTest {

    private static final String SECRET_KEY = randomKey();
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");

    private LocalJwtAuthenticationFilter filter;
    private CapturingChain chain;

    // 하위 서비스로 넘어간 요청을 기록하는 체인 (호출되지 않으면 exchange 는 null)
    private static class CapturingChain implements GatewayFilterChain {
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange = exchange;
            return Mono.empty();
        }

        HttpHeaders forwardedHeaders() {
            return exchange.getRequest().getHeaders();
        }
    }

    private static String randomKey() {
        byte[] keyBytes = new byte[64];
        new SecureRandom().nextBytes(keyBytes);
        return Encoders.BASE64URL.encode(keyBytes);
    }

    @BeforeEach
    void setUp() {
        filter = new LocalJwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "secretKey", SECRET_KEY);
        chain = new CapturingChain();
    }

    // user-service 의 JwtUtil 과 같은 형태의 토큰 (HS512, username + 사용자 정보 claim)
    private JwtBuilder tokenBuilder(String signingKey, Map<String, String> claims, long expirationMillis) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .claim("username", "tester")
                .issuer("user-service")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(signingKey)), Jwts.SIG.HS512);
        claims.forEach(builder::claim);
        return builder;
    }

    private Map<String, String> userClaims(String role) {
        return Map.of(
                "userId", USER_ID.toString(),
                "role", role,
                "affiliationType", "COMPANY",
                "affiliationId", AFFILIATION_ID.toString());
    }

    private String validToken(String role) {
        return tokenBuilder(SECRET_KEY, userClaims(role), 60_000).compact();
    }

    private MockServerWebExchange exchange(String path, String token, String... extraHeaders) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        for (int i = 0; i < extraHeaders.length; i += 2) {
            request.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return MockServerWebExchange.from(request);
    }

    // 401 과 함께 공통 에러 형식 {code, message} 본문을 반환하는지 확인 (토큰 없음: UNAUTHORIZED, 토큰 검증 실패: INVALID_TOKEN)
    private void assertUnauthorized(String expectedCode, MockServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());

        String body = exchange.getResponse().getBodyAsString().block();
        String expectedMessage = expectedCode.equals("UNAUTHORIZED")
                ? "인증이 필요합니다."
                : "유효하지 않거나 만료된 토큰입니다.";
        assertThat(body).isEqualTo("{\"code\":\"" + expectedCode + "\",\"message\":\"" + expectedMessage + "\"}");
        assertThat(chain.exchange).isNull(); // 하위 서비스로 전달되지 않음
    }

    // ---------------- Happy Path ----------------

    @Test
    @DisplayName("유효한 JWT 면 userId, role, 소속 정보를 X-User-* 헤더로 전달")
    void validToken_forwardsUserHeaders() {
        filter.filter(exchange("/companies", validToken("USER")), chain).block();

        HttpHeaders headers = chain.forwardedHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo(USER_ID.toString());
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("USER");
        assertThat(headers.getFirst("X-User-Affiliation-Type")).isEqualTo("COMPANY");
        assertThat(headers.getFirst("X-User-Affiliation-Id")).isEqualTo(AFFILIATION_ID.toString());
    }

    @Test
    @DisplayName("MASTER 토큰이면 X-User-Role 이 MASTER")
    void masterToken_forwardsMasterRole() {
        filter.filter(exchange("/orders", validToken("MASTER")), chain).block();

        assertThat(chain.forwardedHeaders().getFirst("X-User-Role")).isEqualTo("MASTER");
    }

    @Test
    @DisplayName("Authorization 헤더는 그대로 전달 (user-service 자체 인증에서 사용)")
    void validToken_keepsAuthorizationHeader() {
        String token = validToken("USER");
        filter.filter(exchange("/users/me", token), chain).block();

        assertThat(chain.forwardedHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    // ---------------- Unhappy Path ----------------

    @Test
    @DisplayName("JWT 가 없으면 401")
    void noToken_unauthorized() {
        assertUnauthorized("UNAUTHORIZED", exchange("/companies", null));
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 401")
    void notBearer_unauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/companies").header(HttpHeaders.AUTHORIZATION, validToken("USER")));
        assertUnauthorized("UNAUTHORIZED", exchange);
    }

    @Test
    @DisplayName("만료된 JWT 면 401")
    void expiredToken_unauthorized() {
        String expired = tokenBuilder(SECRET_KEY, userClaims("USER"), -60_000).compact();
        assertUnauthorized("INVALID_TOKEN", exchange("/companies", expired));
    }

    @Test
    @DisplayName("다른 키로 서명된 JWT 면 401")
    void wrongSignature_unauthorized() {
        String forged = tokenBuilder(randomKey(), userClaims("MASTER"), 60_000).compact();
        assertUnauthorized("INVALID_TOKEN", exchange("/companies", forged));
    }

    @Test
    @DisplayName("필수 claim(userId, role, affiliationType, affiliationId) 이 하나라도 없으면 401")
    void missingClaim_unauthorized() {
        for (String missing : new String[]{"userId", "role", "affiliationType", "affiliationId"}) {
            Map<String, String> claims = new java.util.HashMap<>(userClaims("USER"));
            claims.remove(missing);
            String token = tokenBuilder(SECRET_KEY, claims, 60_000).compact();

            chain = new CapturingChain();
            assertUnauthorized("INVALID_TOKEN", exchange("/companies", token));
        }
    }

    @Test
    @DisplayName("username 만 있는 기존 형식 토큰이면 401")
    void legacyUsernameOnlyToken_unauthorized() {
        String legacy = tokenBuilder(SECRET_KEY, Map.of(), 60_000).compact();
        assertUnauthorized("INVALID_TOKEN", exchange("/companies", legacy));
    }

    @Test
    @DisplayName("userId 나 affiliationId 가 UUID 형식이 아니면 401")
    void invalidUuidClaim_unauthorized() {
        Map<String, String> badUserId = new java.util.HashMap<>(userClaims("USER"));
        badUserId.put("userId", "1");
        assertUnauthorized("INVALID_TOKEN", exchange("/companies", tokenBuilder(SECRET_KEY, badUserId, 60_000).compact()));

        Map<String, String> badAffiliation = new java.util.HashMap<>(userClaims("USER"));
        badAffiliation.put("affiliationId", "not-a-uuid");
        chain = new CapturingChain();
        assertUnauthorized("INVALID_TOKEN", exchange("/companies", tokenBuilder(SECRET_KEY, badAffiliation, 60_000).compact()));
    }

    @Test
    @DisplayName("JWT 없이 X-User-Role: MASTER 를 보내도 401 (헤더를 신뢰하지 않음)")
    void spoofedHeaderWithoutToken_unauthorized() {
        assertUnauthorized("UNAUTHORIZED", exchange("/orders", null,
                "X-User-Id", UUID.randomUUID().toString(), "X-User-Role", "MASTER"));
    }

    @Test
    @DisplayName("정상 JWT 와 위조 X-User-* 헤더를 함께 보내면 JWT 값으로 교체하고 다른 X-User-* 헤더도 제거")
    void spoofedHeaderWithToken_replacedByJwt() {
        filter.filter(exchange("/orders", validToken("USER"),
                "X-User-Id", "11111111-1111-1111-1111-111111111111",
                "X-User-Role", "MASTER",
                "x-user-affiliation-type", "HUB",
                "X-User-Affiliation-Id", "22222222-2222-2222-2222-222222222222",
                "X-User-affiliationType", "HUB",
                "X-User-Anything", "spoof"), chain).block();

        HttpHeaders headers = chain.forwardedHeaders();
        assertThat(headers.get("X-User-Id")).containsExactly(USER_ID.toString());
        assertThat(headers.get("X-User-Role")).containsExactly("USER");
        assertThat(headers.get("X-User-Affiliation-Type")).containsExactly("COMPANY");
        assertThat(headers.get("X-User-Affiliation-Id")).containsExactly(AFFILIATION_ID.toString());
        assertThat(headers.get("X-User-affiliationType")).isNull();
        assertThat(headers.get("X-User-Anything")).isNull();
    }

    @Test
    @DisplayName("로그인·회원가입 경로는 JWT 없이 통과하지만 위조 X-User-* 헤더는 제거")
    void excludedPath_stripsUserHeaders() {
        for (String path : new String[]{"/auth/login", "/auth/signup"}) {
            chain = new CapturingChain();
            MockServerWebExchange exchange = exchange(path, null,
                    "X-User-Id", UUID.randomUUID().toString(), "X-User-Role", "MASTER");

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull(); // 401 로 막지 않음
            assertThat(chain.forwardedHeaders().get("X-User-Id")).isNull();
            assertThat(chain.forwardedHeaders().get("X-User-Role")).isNull();
        }
    }
}
