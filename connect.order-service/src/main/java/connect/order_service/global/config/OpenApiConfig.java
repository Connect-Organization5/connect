package connect.order_service.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger UI(/swagger-ui/index.html), OpenAPI 문서(/v3/api-docs) 기본 정보
// 문서화 설정일 뿐 인증·인가 동작은 바꾸지 않는다 (Order Service 는 Gateway 가 넣어 준 X-User-* 헤더로 권한을 판단)
@Configuration
public class OpenApiConfig {

    public static final String GATEWAY_AUTH = "gatewayBearerAuth";

    private static final String DESCRIPTION = """
            주문(Order)을 생성·조회·수정·취소하는 API입니다.
            주문 생성 시 hub-service 로 재고를 차감하고 delivery-service 로 배송을 생성하며, 완료되면 Slack 으로 알림을 보냅니다.

            ### 인증 흐름
            - **실제 서비스 (Gateway 경유, 8080)** — 통합 Swagger UI `http://localhost:8080/swagger-ui/index.html`
              1. 클라이언트가 `Authorization: Bearer {JWT}` 로 Gateway 에 요청합니다. (Swagger UI 의 Authorize 에 토큰 입력)
              2. Gateway 가 JWT 를 검증한 뒤 `X-User-Id`, `X-User-Role`, `X-User-Affiliation-Type`, `X-User-Affiliation-Id` 헤더를 JWT 값으로 설정해 전달합니다.
              3. Order Service 는 JWT 를 직접 검증하지 않고 전달받은 `X-User-*` 헤더로 권한을 판단합니다. (`X-User-Id` 는 주문의 `createdBy` 로도 저장됩니다)
            - **Order Service 자체 Swagger UI (직접 접근, 8084)** — `http://localhost:8084/swagger-ui/index.html`
              - Gateway 를 거치지 않으므로 JWT 검증이 없습니다. 각 API 의 `X-User-*` 헤더를 직접 입력해 호출합니다.
              - 로컬 개발·테스트 용도입니다. 운영에서는 서비스 포트를 외부에 노출하지 않고 Gateway 로만 접근해야 합니다.

            ### 주문 상태
            - `PENDING` → `CREATED`: 저장 후 재고 차감과 배송 생성이 모두 성공
            - `PENDING` → `FAILED`: 재고 부족·판매 중단·판매 준비 중, 재고 차감 오류, 배송 생성 실패, 최종 저장 실패 (실패한 주문도 기록으로 남습니다)
              - 실패 시 이미 처리된 재고 차감·배송 생성은 되돌리기(재고 복구, 배송 취소)를 시도합니다.
            - `CREATED` / `FAILED` → `CANCELED`: 주문 취소(삭제). 논리 삭제되어 이후 조회되지 않습니다.
            - 수정은 `CREATED` 이고 배송이 `HUB_WAITING` 일 때만 가능합니다.
            - 취소는 배송이 `HUB_WAITING`(또는 이미 `CANCELED`) 일 때만 가능하며, 배송·재고 처리는 `CREATED` 주문에만 수행합니다.

            ### 권한
            - `MASTER` (`X-User-Role`, 대소문자 무시): 모든 주문 조회·수정·취소, 받은 주문 전체 조회
            - 주문자 (`createdBy == X-User-Id`): 본인 주문 조회·수정·취소
            - 공급 업체 담당자 (공급 업체의 `userId == X-User-Id`): 받은 주문 조회. 목록 조회는 `COMPANY` 소속이어야 합니다.
            - 주문 생성은 `X-User-Affiliation-Type` 이 `COMPANY` 또는 `HUB` 인 사용자만 가능합니다.

            ### 응답 형식
            - 성공: `{ "success": true, "message": "...", "data": { ... }, "timestamp": "..." }`
            - 실패: `{ "success": false, "message": "주문을 찾을 수 없습니다.", "data": null, "timestamp": "..." }` (HTTP 상태는 응답 상태 코드)
              - 응답 본문에 에러 코드 필드는 없습니다. 각 API 설명의 괄호 안 이름(`ORDER_NOT_FOUND` 등)은 내부 ErrorCode 이며 `message` 로 구분합니다.
              - 연관 서비스(company, hub, delivery) 호출이 별도 처리 없이 실패하면 502 로 응답합니다.
            """;

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description(DESCRIPTION)
                        .version("v1"))
                // 상대 경로: 문서를 연 주소로 호출한다 (Gateway 8080 이면 Gateway 경유, 8084 이면 직접 호출)
                .servers(List.of(
                        new Server().url("/").description("현재 Swagger UI 주소 기준 (Gateway 8080 이면 Gateway 경유, 8084 이면 직접 호출)")))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Gateway 경유 시 사용하는 JWT. Order Service 는 이 값을 직접 검증하지 않습니다.")));
    }
}
