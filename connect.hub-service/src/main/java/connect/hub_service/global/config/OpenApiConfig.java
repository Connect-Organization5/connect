package connect.hub_service.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger UI(/swagger-ui/index.html), OpenAPI 문서(/v3/api-docs) 기본 정보
// 문서화 설정일 뿐 인증·인가 동작은 바꾸지 않는다 (Hub Service 는 Gateway 가 넣어 준 X-User-* 헤더로 권한을 판단)
@Configuration
public class OpenApiConfig {

    public static final String GATEWAY_AUTH = "gatewayBearerAuth";

    private static final String DESCRIPTION = """
            허브(Hub), 허브 간 이동 경로(HubRoute), 허브별 상품 재고(Stock)를 관리하는 API입니다.

            ### 인증 흐름
            - **실제 서비스 (Gateway 경유, 8080)** — 통합 Swagger UI `http://localhost:8080/swagger-ui/index.html`
              1. 클라이언트가 `Authorization: Bearer {JWT}` 로 Gateway 에 요청합니다. (Swagger UI 의 Authorize 에 토큰 입력)
              2. Gateway 가 JWT 를 검증한 뒤 `X-User-Id`, `X-User-Role` 등 `X-User-*` 헤더를 JWT 값으로 설정해 전달합니다.
              3. Hub Service 는 JWT 를 직접 검증하지 않고 전달받은 `X-User-*` 헤더로 권한을 판단합니다.
            - **Hub Service 자체 Swagger UI (직접 접근, 8083)** — `http://localhost:8083/swagger-ui/index.html`
              - Gateway 를 거치지 않으므로 JWT 검증이 없습니다. 각 API 의 `X-User-*` 헤더를 직접 입력해 호출합니다.
              - 로컬 개발·테스트 용도입니다. 운영에서는 서비스 포트를 외부에 노출하지 않고 Gateway 로만 접근해야 합니다.

            ### 권한
            - 허브·이동 경로·재고 조회: 별도 권한 검사 없음 (Gateway 의 JWT 검증만 통과하면 됨)
            - 재고 등록·입고·입고 이력 조회: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`). company-service 에 확인합니다. (입고 이력 조회는 MASTER 면 확인 생략)

            ### 페이징
            - `page` 는 0 이상, `size` 는 1~100 (기본 `page=0`, `size=10`). 벗어나면 400 입니다.

            ### 응답 형식
            - 성공: `{ "success": true, "message": "...", "data": { ... }, "timestamp": "..." }`
            - 실패: `{ "success": false, "code": "HUB_NOT_FOUND", "message": "허브를 찾을 수 없습니다.", "data": null, "timestamp": "..." }` (HTTP 상태는 응답 상태 코드)
            - `code` 는 실패 응답에만 들어가며, 각 API 의 오류 설명 괄호 안에 적힌 값입니다. 같은 HTTP 상태 안에서 원인을 구분할 때 사용합니다. (예: order-service 는 409 의 `INSUFFICIENT_STOCK` / `STOCK_DELETED` 를 구분)

            ### 내부 API (`/internal/**`)
            - order-service, company-service 가 Feign 으로 호출하는 서비스 간 API 입니다. Gateway 라우트에 없어 외부에서는 호출할 수 없습니다.
            - 통합 Swagger UI(Gateway 8080) 에서 Try it out 하면 404 가 나오는 것이 정상입니다.
            """;

    @Bean
    public OpenAPI hubServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hub Service API")
                        .description(DESCRIPTION)
                        .version("v1"))
                // 상대 경로: 문서를 연 주소로 호출한다 (Gateway 8080 이면 Gateway 경유, 8083 이면 직접 호출)
                .servers(List.of(
                        new Server().url("/").description("현재 Swagger UI 주소 기준 (Gateway 8080 이면 Gateway 경유, 8083 이면 직접 호출)")))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Gateway 경유 시 사용하는 JWT. Hub Service 는 이 값을 직접 검증하지 않습니다.")));
    }
}
