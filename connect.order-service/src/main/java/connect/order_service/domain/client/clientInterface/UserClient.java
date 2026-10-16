package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.response.UserInfo;

import java.util.UUID;

public interface UserClient {
    UserInfo getUser(UUID userId);
}
