package connect.order_service.domain.client;

import connect.order_service.domain.client.dto.response.UserInfo;
import connect.order_service.domain.client.clientInterface.UserClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

//@FeignClient(name = "user-service")
@Component
public class UserFeignClient implements UserClient {
    @Override
    public UserInfo getUser(UUID userId) {
        return new UserInfo(
                userId,
                "aa",   // 테스트할 때 본인 Slack 멤버 ID로 바꾸면 멘션 확인 가능
                "HUB",                                                     // COMPANY → HUB
                UUID.fromString("d11a5656-9f1a-3d1f-974a-a3703ac696fb"));  // 실제 허브 ID
    }
}
