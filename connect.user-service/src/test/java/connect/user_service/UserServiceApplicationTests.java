package connect.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// test 프로파일: H2 사용, Eureka 등록 안 함 (src/test/resources/application-test.yml)
@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
