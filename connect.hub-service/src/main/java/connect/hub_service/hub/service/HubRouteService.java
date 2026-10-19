package connect.hub_service.hub.service;

import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import connect.hub_service.global.util.PageRequests;
import connect.hub_service.hub.dto.HubRouteResponse;
import connect.hub_service.hub.entity.HubRoute;
import connect.hub_service.hub.repository.HubRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HubRouteService {

    private static final Sort DEFAULT_SORT = Sort.by("departureHub.id").and(Sort.by("arrivalHub.id"));

    private final HubRouteRepository hubRouteRepository;

    // 출발·도착 조건은 각각 선택이며 둘 다 있으면 AND
    public PagedResponse<HubRouteResponse> getRoutes(UUID departureHubId, UUID arrivalHubId, int page, int size) {
        PageRequest pageRequest = PageRequests.of(page, size, DEFAULT_SORT);

        Page<HubRoute> routes;
        if (departureHubId != null && arrivalHubId != null) {
            routes = hubRouteRepository.findByDepartureHubIdAndArrivalHubId(departureHubId, arrivalHubId, pageRequest);
        } else if (departureHubId != null) {
            routes = hubRouteRepository.findByDepartureHubId(departureHubId, pageRequest);
        } else if (arrivalHubId != null) {
            routes = hubRouteRepository.findByArrivalHubId(arrivalHubId, pageRequest);
        } else {
            routes = hubRouteRepository.findAll(pageRequest);
        }
        return PagedResponse.from(routes.map(HubRouteResponse::from));
    }

    public HubRouteResponse getRoute(UUID departureHubId, UUID arrivalHubId) {
        HubRoute route = hubRouteRepository.findByDepartureHubIdAndArrivalHubId(departureHubId, arrivalHubId)
                .orElseThrow(() -> new BaseException(ErrorCode.ROUTE_NOT_FOUND));
        return HubRouteResponse.from(route);
    }
}
