package connect.user_service.domain.user.dto.response;

import java.util.UUID;

public record HubInfo(
        UUID hubId,
        String name,
        String address) {

}
