package connect.user_service.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger UI(/swagger-ui/index.html), OpenAPI 문서(/v3/api-docs) 기본 정보
// 문서화 설정일 뿐 인증·인가 동작은 바꾸지 않는다 (User Service 는 JWT 를 발급하고, Authorization 헤더의 JWT 를 직접 검증)
@Configuration
public class OpenApiConfig {

    public static final String GATEWAY_AUTH = "gatewayBearerAuth";

    private static final String DESCRIPTION = """
            회원가입·로그인(JWT 발급)과 회원(User) 관리를 담당하는 API입니다.

            ### 인증 흐름
            - **로그인**: `POST /auth/login` 응답의 `data.access_token` 이 JWT 입니다. (`Bearer ` 접두사 없이 토큰만 반환)
              - Swagger UI 의 Authorize 에 이 토큰을 입력하면 이후 요청에 `Authorization: Bearer {JWT}` 가 붙습니다.
              - `/auth/signup`, `/auth/login` 은 토큰 없이 호출합니다.
            - **실제 서비스 (Gateway 경유, 8080)** — 통합 Swagger UI `http://localhost:8080/swagger-ui/index.html`
              1. 클라이언트가 `Authorization: Bearer {JWT}` 로 Gateway 에 요청합니다.
              2. Gateway 가 JWT 를 검증하고, `Authorization` 헤더는 그대로 User Service 로 전달합니다.
              3. User Service 는 `X-User-*` 헤더를 사용하지 않고 **JWT 를 직접 다시 검증**한 뒤, 토큰의 `username` 으로 DB 에서 회원을 조회해 인증합니다.
            - **User Service 자체 Swagger UI (직접 접근, 19090)** — `http://localhost:19090/swagger-ui/index.html`
              - Gateway 를 거치지 않아도 JWT 검증은 그대로 적용됩니다. `/auth/**`, `/internal/**` 외의 API 는 Authorize 에 토큰을 입력해야 합니다.
              - 로컬 개발·테스트 용도입니다. 운영에서는 서비스 포트를 외부에 노출하지 않고 Gateway 로만 접근해야 합니다.
            - 토큰이 없으면 401(`UNAUTHORIZED`), 서명 불일치·만료·삭제된 회원의 토큰이면 401(`INVALID_TOKEN`) 입니다.

            ### 권한
            - 권한(`role`): `MASTER`(관리자), `USER`(일반 사용자). 회원가입은 항상 `USER` 로 생성되며 `MASTER` 만 권한을 바꿀 수 있습니다.
            - 소속 유형(`affiliationType`): `COMPANY`(업체), `HUB`(허브). 가입 시 정하며 이후 변경할 수 없습니다.
            - `MASTER`: 모든 회원 조회·목록 조회·수정·삭제 (소속과 관계없음)
            - `USER`: 본인 정보 조회만 가능. 다른 회원 조회·목록·수정·삭제는 403(`USER_ACCESS_DENIED`)
            - 마지막 `MASTER` 는 삭제하거나 `USER` 로 바꿀 수 없습니다. (409)

            ### 응답 형식
            - 성공: `{ "success": true, "message": "...", "data": { ... }, "timestamp": "..." }` (회원 삭제는 본문 없이 204)
            - 실패: `{ "code": "USER_NOT_FOUND", "message": "유저를 찾을 수 없습니다." }` (HTTP 상태는 응답 상태 코드)
              - 유효성 검사 실패(`VALIDATION_FAILED`)는 `message` 에 `필드: 사유` 목록이 담깁니다.

            ### 내부 API (`/internal/**`)
            - company-service, order-service, delivery-service 가 Feign 으로 호출하는 서비스 간 API 입니다. JWT 없이 호출하며, Gateway 라우트에 없어 외부에서는 호출할 수 없습니다.
            - 통합 Swagger UI(Gateway 8080) 에서 Try it out 하면 404 가 나오는 것이 정상입니다.
            """;

    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .description(DESCRIPTION)
                        .version("v1"))
                // 상대 경로: 문서를 연 주소로 호출한다 (Gateway 8080 이면 Gateway 경유, 19090 이면 직접 호출)
                .servers(List.of(
                        new Server().url("/").description("현재 Swagger UI 주소 기준 (Gateway 8080 이면 Gateway 경유, 19090 이면 직접 호출)")))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("POST /auth/login 으로 발급받은 JWT (data.access_token). Gateway 와 User Service 모두 이 값을 검증하므로 직접 호출(19090) 시에도 필요합니다.")));
    }
}
