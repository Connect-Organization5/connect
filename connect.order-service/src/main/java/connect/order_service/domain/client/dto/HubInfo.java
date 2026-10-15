package connect.order_service.domain.client.dto;

import java.util.UUID;

public record HubInfo(
        UUID hubId,
        String name,
        String address) {

}
