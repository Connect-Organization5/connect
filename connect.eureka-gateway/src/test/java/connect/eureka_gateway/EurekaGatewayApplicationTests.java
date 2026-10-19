package connect.eureka_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// test 프로파일: Eureka 등록 안 함 (src/test/resources/application-test.yml)
@SpringBootTest
@ActiveProfiles("test")
class EurekaGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
