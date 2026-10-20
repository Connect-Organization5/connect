package connect.user_service.domain.client.clientinterface;

import connect.user_service.domain.user.dto.response.HubInfo;

import java.util.UUID;

public interface HubClient {
    HubInfo getHub(UUID hubId);

}