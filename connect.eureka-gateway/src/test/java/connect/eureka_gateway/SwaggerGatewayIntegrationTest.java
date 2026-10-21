package connect.eureka_gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Gateway 를 띄워 통합 Swagger UI 설정과 인증 동작을 확인
// Eureka 는 꺼져 있으므로 lb:// 대상이 있도록 Simple Discovery 로 가짜 인스턴스(응답하지 않는 포트)를 등록한다
// (인스턴스가 없으면 LoadBalancer 가 JWT 필터보다 먼저 503 을 반환해 인증 동작을 확인할 수 없음)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.discovery.client.simple.instances.company-service[0].uri=http://localhost:1",
        "spring.cloud.discovery.client.simple.instances.hub-service[0].uri=http://localhost:1",
        "spring.cloud.discovery.client.simple.instances.order-service[0].uri=http://localhost:1",
        "spring.cloud.discovery.client.simple.instances.user-service[0].uri=http://localhost:1"
})
@ActiveProfiles("test")
class SwaggerGatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Gateway 에서 Swagger UI 를 JWT 없이 제공")
    void swaggerUi_isServed() throws Exception {
        HttpResponse<String> response = get("/swagger-ui/index.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("swagger-ui");
    }

    @Test
    @DisplayName("Swagger 설정에 서비스 선택 드롭다운(company-service → /docs/company/v3/api-docs) 이 있음")
    void swaggerConfig_listsCompanyService() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs/swagger-config");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"name\":\"company-service\"")
                .contains("\"url\":\"/docs/company/v3/api-docs\"");
    }

    @Test
    @DisplayName("모든 서비스의 문서가 드롭다운에 있고, 각 문서 라우트가 해당 서비스로 연결됨")
    void swaggerConfig_listsAllServices() throws Exception {
        String body = get("/v3/api-docs/swagger-config").body();
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        for (String service : new String[]{"company", "delivery", "user", "hub", "order"}) {
            assertThat(body).contains("\"url\":\"/docs/" + service + "/v3/api-docs\"");
            RouteDefinition docs = routes.stream().filter(r -> r.getId().equals(service + "-service-docs")).findFirst().orElseThrow();
            assertThat(docs.getUri().toString()).isEqualTo("lb://" + service + "-service");
        }
    }

    @Test
    @DisplayName("company 문서 라우트가 StripPrefix=2 로 lb://company-service 에 연결되고 기존 API 라우트도 유지")
    void routes_includeCompanyDocsAndKeepExistingRoutes() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).extracting(RouteDefinition::getId)
                .contains("company-service-docs", "user-service", "hub-service", "company-service", "order-service", "delivery-service");

        RouteDefinition docs = routes.stream().filter(r -> r.getId().equals("company-service-docs")).findFirst().orElseThrow();
        assertThat(docs.getUri().toString()).isEqualTo("lb://company-service");
        assertThat(docs.getPredicates()).singleElement()
                .satisfies(p -> assertThat(p.getArgs().values()).containsExactly("/docs/company/v3/api-docs"));
        assertThat(docs.getFilters()).singleElement()
                .satisfies(f -> {
                    assertThat(f.getName()).isEqualTo("StripPrefix");
                    assertThat(f.getArgs().values()).containsExactly("2");
                });

        // /internal/** 은 어떤 라우트로도 노출하지 않음
        assertThat(routes).flatExtracting(RouteDefinition::getPredicates)
                .allSatisfy(p -> assertThat(p.getArgs().values().toString()).doesNotContain("/internal"));
    }

    @Test
    @DisplayName("문서 라우트는 JWT 없이도 401 이 아님 (가짜 인스턴스라 연결 실패 응답)")
    void companyDocs_doesNotRequireToken() throws Exception {
        HttpResponse<String> response = get("/docs/company/v3/api-docs");

        assertThat(response.statusCode()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("실제 보호 API 는 JWT 없으면 그대로 401")
    void protectedApi_stillRequiresToken() throws Exception {
        for (String path : new String[]{"/companies", "/companies/00000000-0000-0000-0000-000000000001/products", "/hubs", "/orders", "/users/me"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(response.body()).as(path).contains("\"code\":\"UNAUTHORIZED\"");
        }
    }

    @Test
    @DisplayName("잘못된 JWT 도 401 INVALID_TOKEN")
    void invalidToken_unauthorized() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/companies"))
                        .header("Authorization", "Bearer not-a-valid-jwt").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"INVALID_TOKEN\"");
    }
}
