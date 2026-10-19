package connect.hub_service.hub.dto;

import connect.hub_service.hub.entity.Hub;

import java.util.UUID;

public record HubResponse(UUID hubId, String name, String address) {

    public static HubResponse from(Hub hub) {
        return new HubResponse(hub.getId(), hub.getName(), hub.getAddress());
    }
}
