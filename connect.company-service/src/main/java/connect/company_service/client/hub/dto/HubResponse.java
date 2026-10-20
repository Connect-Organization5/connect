package connect.company_service.client.hub.dto;

import java.util.UUID;

// Hub Service GET /hubs/{hubId} 응답의 data 부분
public record HubResponse(
        UUID hubId,
        String name,
        String address
) {
}
