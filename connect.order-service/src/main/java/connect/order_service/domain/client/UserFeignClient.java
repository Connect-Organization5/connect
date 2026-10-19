package connect.order_service.domain.client;

import connect.order_service.domain.client.Api.UserFeignApi;
import connect.order_service.domain.client.dto.response.UserInfo;
import connect.order_service.domain.client.clientInterface.UserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserFeignClient implements UserClient {
    private final UserFeignApi userFeignApi;

    @Override
    public UserInfo getUser(UUID userId) {
        return userFeignApi.getUser(userId).getData();
    }
}
