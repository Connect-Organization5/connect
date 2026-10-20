package connect.delivery_service.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger UI(/swagger-ui/index.html), OpenAPI 문서(/v3/api-docs) 기본 정보
// 문서화 설정일 뿐 인증·인가 동작은 바꾸지 않는다 (Delivery Service 는 Gateway 가 넣어 준 X-User-* 헤더로 권한을 판단)
@Configuration
public class OpenApiConfig {

    public static final String GATEWAY_AUTH = "gatewayBearerAuth";

    private static final String DESCRIPTION = """
            배송(Delivery)과 배송 경로(DeliveryRoute)를 관리하는 API입니다.

            ### 인증 흐름
            - **실제 서비스 (Gateway 경유, 8080)** — 통합 Swagger UI `http://localhost:8080/swagger-ui/index.html`
              1. 클라이언트가 `Authorization: Bearer {JWT}` 로 Gateway 에 요청합니다. (Swagger UI 의 Authorize 에 토큰 입력)
              2. Gateway 가 JWT 를 검증한 뒤 `X-User-Id`, `X-User-Role`, `X-User-Affiliation-Type` 헤더를 JWT 값으로 설정해 전달합니다.
              3. Delivery Service 는 JWT 를 직접 검증하지 않고 전달받은 `X-User-*` 헤더로 권한을 판단합니다.
            - **Delivery Service 자체 Swagger UI (직접 접근, 8085)** — `http://localhost:8085/swagger-ui/index.html`
              - Gateway 를 거치지 않으므로 JWT 검증이 없습니다. 각 API 의 `X-User-*` 헤더를 직접 입력해 호출합니다.
              - 로컬 개발·테스트 용도입니다. 운영에서는 서비스 포트를 외부에 노출하지 않고 Gateway 로만 접근해야 합니다.

            ### 배송 상태
            `HUB_WAITING` → `HUB_MOVING` → `DEST_HUB_ARRIVED` → `OUT_FOR_DELIVERY` → `DELIVERED`
            (`HUB_WAITING` 에서만 `CANCELED` 가능)

            ### 권한
            - `MASTER`: 모든 배송 조회·수정·삭제, 담당자 변경
            - 주문자 (`ordererId == X-User-Id`): 본인 주문의 배송 조회, 수령인 변경, 삭제
            - 배송 담당자 (`deliveryUserId == X-User-Id`): 담당 배송 조회, 상태 변경
            - 권한이 없으면 배송의 존재 여부를 숨기기 위해 404(`DELIVERY_NOT_FOUND`)로 응답합니다.

            ### 응답 형식
            - 성공: `{ "success": true, "message": "...", "data": { ... }, "timestamp": "..." }`
            - 실패: `{ "code": "DELIVERY_NOT_FOUND", "message": "배송을 찾을 수 없습니다." }` (HTTP 상태는 응답 상태 코드)

            ### 내부 API (`/internal/**`)
            - order-service 가 Feign 으로 호출하는 서비스 간 API 입니다. Gateway 라우트에 없어 외부에서는 호출할 수 없습니다.
            - 통합 Swagger UI(Gateway 8080) 에서 Try it out 하면 404 가 나오는 것이 정상입니다.
            """;

    @Bean
    public OpenAPI deliveryServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Delivery Service API")
                        .description(DESCRIPTION)
                        .version("v1"))
                // 상대 경로: 문서를 연 주소로 호출한다 (Gateway 8080 이면 Gateway 경유, 8085 이면 직접 호출)
                .servers(List.of(
                        new Server().url("/").description("현재 Swagger UI 주소 기준 (Gateway 8080 이면 Gateway 경유, 8085 이면 직접 호출)")))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Gateway 경유 시 사용하는 JWT. Delivery Service 는 이 값을 직접 검증하지 않습니다.")));
    }
}
