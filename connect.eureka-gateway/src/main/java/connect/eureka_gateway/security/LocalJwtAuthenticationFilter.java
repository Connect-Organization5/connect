package connect.eureka_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Component
@Slf4j
public class LocalJwtAuthenticationFilter implements GlobalFilter {

    // 하위 서비스로 전달하는 사용자 정보 헤더 (JWT 에서 검증된 값으로만 채움)
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";
    static final String AFFILIATION_TYPE_HEADER = "X-User-Affiliation-Type";
    static final String AFFILIATION_ID_HEADER = "X-User-Affiliation-Id";

    // 클라이언트가 보낸 사용자 정보 헤더는 신뢰하지 않으므로 이 접두사로 시작하면 모두 제거
    private static final String USER_HEADER_PREFIX = "x-user-";

    // user-service 의 JwtUtil 이 발급하는 claim 이름
    private static final String USER_ID_CLAIM = "userId";
    private static final String ROLE_CLAIM = "role";
    private static final String AFFILIATION_TYPE_CLAIM = "affiliationType";
    private static final String AFFILIATION_ID_CLAIM = "affiliationId";

    // 401 응답 본문 (user-service ErrorCode 의 UNAUTHORIZED, INVALID_TOKEN 과 같은 값)
    static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";
    static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다.";
    static final String INVALID_TOKEN_CODE = "INVALID_TOKEN";
    static final String INVALID_TOKEN_MESSAGE = "유효하지 않거나 만료된 토큰입니다.";

    @Value("${service.jwt.secret-key}")
    private String secretKey;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // /auth/login, /auth/signup 으로 들어올 때 Token 체크 안하게 설정 (위조 사용자 헤더는 제거해서 전달)
        if (path.equals("/auth/login") || path.equals("/auth/signup")) {
            return chain.filter(withUserHeaders(exchange, null));
        }

        String token  = extractToken(exchange);
        if (token == null) {
            return unauthorized(exchange, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
        }

        Claims claims = parseValidClaims(token);
        if (claims == null){
            return unauthorized(exchange, INVALID_TOKEN_CODE, INVALID_TOKEN_MESSAGE);
        }

        return chain.filter(withUserHeaders(exchange, claims));
    }

    // 401 응답에 공통 에러 형식 {code, message} 본문을 담아 반환 (user-service 의 ErrorCode 와 같은 코드·메시지)
    private Mono<Void> unauthorized(ServerWebExchange exchange, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));

        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);        }
        return null;
    }

    // 서명, 만료 시간, 필수 사용자 claim 을 확인한 뒤 Claims 반환. 하나라도 실패하면 null (토큰 내용은 로그에 남기지 않음)
    private Claims parseValidClaims(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secretKey));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build().parseSignedClaims(token)
                    .getPayload();

            if (!hasRequiredUserClaims(claims)) {
                log.warn("JWT 에 필수 사용자 정보가 없거나 형식이 올바르지 않습니다.");
                return null;
            }
            return claims;
        } catch (Exception e) {
            // 서명 불일치, 만료, 형식 오류 등 (예외 메시지에 토큰 일부가 담길 수 있어 출력하지 않음)
            return null;
        }
    }

    private boolean hasRequiredUserClaims(Claims claims) {
        return isUuid(claims.get(USER_ID_CLAIM, String.class))
                && StringUtils.hasText(claims.get(ROLE_CLAIM, String.class))
                && StringUtils.hasText(claims.get(AFFILIATION_TYPE_CLAIM, String.class))
                && isUuid(claims.get(AFFILIATION_ID_CLAIM, String.class));
    }

    private boolean isUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // 클라이언트가 보낸 X-User-* 헤더를 모두 지우고, claims 가 있으면 검증된 값으로 다시 설정
    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, Claims claims) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    removeUserHeaders(headers);
                    if (claims != null) {
                        headers.set(USER_ID_HEADER, claims.get(USER_ID_CLAIM, String.class));
                        headers.set(USER_ROLE_HEADER, claims.get(ROLE_CLAIM, String.class));
                        headers.set(AFFILIATION_TYPE_HEADER, claims.get(AFFILIATION_TYPE_CLAIM, String.class));
                        headers.set(AFFILIATION_ID_HEADER, claims.get(AFFILIATION_ID_CLAIM, String.class));
                    }
                }))
                .build();
    }

    private void removeUserHeaders(HttpHeaders headers) {
        for (String name : new ArrayList<>(headers.headerNames())) {
            if (name.toLowerCase(Locale.ROOT).startsWith(USER_HEADER_PREFIX)) {
                headers.remove(name);
            }
        }
    }
}
