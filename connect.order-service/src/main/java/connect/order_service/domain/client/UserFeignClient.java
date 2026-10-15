package connect.order_service.domain.client;

import connect.order_service.domain.client.dto.UserInfo;
import connect.order_service.domain.client.clientInterface.UserClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

//@FeignClient(name = "user-service")
@Component
public class UserFeignClient implements UserClient {
    @Override
    public UserInfo getUser(UUID userId) {
        return new UserInfo(
                userId,
                "aa");      // 테스트할 때 본인 Slack 멤버 ID로 바꾸면 멘션 확인 가능
    }
}
