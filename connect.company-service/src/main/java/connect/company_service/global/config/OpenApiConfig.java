package connect.company_service.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger UI(/swagger-ui/index.html), OpenAPI 문서(/v3/api-docs) 기본 정보
// 문서화 설정일 뿐 인증·인가 동작은 바꾸지 않는다 (Company Service 는 Gateway 가 넣어 준 X-User-* 헤더로 권한을 판단)
@Configuration
public class OpenApiConfig {

    static final String GATEWAY_AUTH = "gatewayBearerAuth";

    private static final String DESCRIPTION = """
            업체(Company)와 상품(Product)을 관리하는 API입니다.

            ### 인증 흐름
            - **실제 서비스 (Gateway 경유, 8080)** — 통합 Swagger UI `http://localhost:8080/swagger-ui/index.html`
              1. 클라이언트가 `Authorization: Bearer {JWT}` 로 Gateway 에 요청합니다. (Swagger UI 의 Authorize 에 토큰 입력)
              2. Gateway 가 JWT 서명·만료를 검증한 뒤 클라이언트가 보낸 `X-User-*` 헤더를 지우고,
                 JWT 값으로 `X-User-Id`, `X-User-Role`, `X-User-Affiliation-Type`, `X-User-Affiliation-Id` 를 다시 설정해 전달합니다.
                 따라서 Swagger UI 에서 입력한 `X-User-Id`, `X-User-Role` 값은 무시되고 JWT 값이 사용됩니다.
              3. Company Service 는 JWT 를 직접 검증하지 않고 전달받은 `X-User-Id`, `X-User-Role` 로 권한을 판단합니다.
            - **Company Service 자체 Swagger UI (직접 접근, 8082)** — `http://localhost:8082/swagger-ui/index.html`
              - Gateway 를 거치지 않으므로 JWT 검증이 없습니다. 각 API 의 `X-User-Id`, `X-User-Role` 헤더를 직접 입력해 호출합니다.
              - 로컬 개발·테스트 용도입니다. 운영에서는 서비스 포트를 외부에 노출하지 않고 Gateway 로만 접근해야 합니다.
              - 상단 Authorize 의 Bearer 토큰은 Gateway 경유 시 사용합니다. Company Service 는 이 값을 직접 검증하지 않습니다.

            ### 권한
            - `MASTER`: 모든 업체·상품 관리 가능
            - 업체 관리자: `Company.userId == X-User-Id` 인 사용자. 자기 업체와 그 업체의 상품만 관리 가능

            ### 응답 형식
            - 성공: `{ "success": true, "message": "...", "data": { ... }, "timestamp": "..." }`
            - 실패: `{ "code": "COMPANY_NOT_FOUND", "message": "업체를 찾을 수 없습니다." }` (HTTP 상태는 응답 상태 코드)

            ### 내부 API (`/internal/**`)
            - 다른 서비스가 Feign 으로 호출하는 서비스 간 API 입니다. Gateway 라우트에 없어 외부에서는 호출할 수 없습니다.
            - 통합 Swagger UI(Gateway 8080) 에서 Try it out 하면 404 가 나오는 것이 정상입니다.
            """;

    @Bean
    public OpenAPI companyServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Company Service API")
                        .description(DESCRIPTION)
                        .version("v1"))
                // 상대 경로: 문서를 연 주소로 호출한다
                // - Gateway 통합 Swagger UI(8080) 에서 열면 Gateway 경유 → Bearer JWT 필요, X-User-* 는 Gateway 가 JWT 값으로 덮어씀
                // - Company Service 자체 Swagger UI(8082) 에서 열면 직접 호출 → X-User-* 헤더 직접 입력
                .servers(List.of(
                        new Server().url("/").description("현재 Swagger UI 주소 기준 (Gateway 8080 이면 Gateway 경유, 8082 이면 직접 호출)")))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Gateway 경유 시 사용하는 JWT. Company Service 는 이 값을 직접 검증하지 않습니다.")));
    }
}
