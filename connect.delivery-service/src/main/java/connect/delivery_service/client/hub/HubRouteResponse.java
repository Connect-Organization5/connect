package connect.delivery_service.client.hub;

import java.math.BigDecimal;
import java.util.UUID;

// hub-service GET /hubs/routes/{departureHubId}/{arrivalHubId} 응답의 data
// distance(km), duration(분)
public record HubRouteResponse(
        UUID routeId,
        UUID departureHubId,
        UUID arrivalHubId,
        BigDecimal distance,
        Integer duration
) {
}
