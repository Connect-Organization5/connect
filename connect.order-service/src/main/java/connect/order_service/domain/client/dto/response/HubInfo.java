package connect.order_service.domain.client.dto.response;

import java.util.UUID;

public record HubInfo(
        UUID hubId,
        String name,
        String address) {

}
