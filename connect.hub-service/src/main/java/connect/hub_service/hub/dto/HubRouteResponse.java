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

    // 출발·도착 허브가 같으면 이동이 없으므로 저장된 경로 없이 거리·시간 0으로 응답한다. routeId는 null이다.
    public static HubRouteResponse sameHub(UUID hubId) {
        return new HubRouteResponse(null, hubId, hubId, 0.0, 0);
    }
}
