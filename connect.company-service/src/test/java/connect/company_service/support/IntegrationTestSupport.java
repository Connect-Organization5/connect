package connect.company_service.support;

import connect.company_service.client.hub.HubClient;
import connect.company_service.client.hub.HubValidator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// 통합 테스트 공통 설정 (PostgreSQL 컨테이너는 JVM당 한 번만 띄워 모든 테스트 클래스가 공유)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    static {
        postgres.start();
    }

    // 테스트 환경에는 Hub Service가 없으므로 실제 Feign 호출 대신 Mock 사용
    // (Mock 기본 동작: Hub 존재 검증은 통과, 재고 삭제는 아무것도 하지 않음)
    @MockitoBean
    protected HubValidator hubValidator;

    @MockitoBean
    protected HubClient hubClient;
}
