package connect.hub_service.hub.dto;

import connect.hub_service.hub.entity.HubRoute;

import java.util.UUID;

// distance(km), duration(분)은 대표 좌표 기반 추정값이다.
public record HubRouteResponse(UUID routeId, UUID departureHubId, UUID arrivalHubId, Double distance, Integer duration) {

    public static HubRouteResponse from(HubRoute route) {
        return new HubRouteResponse(
                route.getId(),
                route.getDepartureHub().getId(),
                route.getArrivalHub().getId(),
                route.getDistance().doubleValue(),
                route.getDuration());
    }
}
