package connect.hub_service.hub.seed;

import connect.hub_service.hub.entity.Hub;
import connect.hub_service.hub.entity.HubRoute;
import connect.hub_service.hub.repository.HubRepository;
import connect.hub_service.hub.repository.HubRouteRepository;
import connect.hub_service.hub.seed.HubRouteSeedData.RouteSeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 허브 간 경로 초기 데이터(추정값)를 "없는 것만" 삽입한다. 이미 있는 행은 수정하지 않는다.
 * 같은 ID 또는 같은 (출발, 도착) 쌍이 있으면 건너뛰며, 허브가 아직 적재되지 않은 경로도 건너뛴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HubRouteSeedLoader {

    public record Result(int routesInserted, int routesSkipped) {
    }

    private final HubRepository hubRepository;
    private final HubRouteRepository hubRouteRepository;

    @Transactional
    public Result load() {
        Map<UUID, Hub> hubs = hubRepository.findAll().stream()
                .collect(Collectors.toMap(Hub::getId, Function.identity()));

        int inserted = 0;
        int skipped = 0;

        for (RouteSeed seed : HubRouteSeedData.routes()) {
            Hub departure = hubs.get(seed.departureHubId());
            Hub arrival = hubs.get(seed.arrivalHubId());
            if (departure == null || arrival == null
                    || hubRouteRepository.existsById(seed.id())
                    || hubRouteRepository.existsByDepartureHubIdAndArrivalHubId(departure.getId(), arrival.getId())) {
                skipped++;
                continue;
            }
            hubRouteRepository.save(new HubRoute(seed.id(), departure, arrival, BigDecimal.valueOf(seed.distance()), seed.duration()));
            inserted++;
        }

        Result result = new Result(inserted, skipped);
        log.info("허브 경로 초기 데이터 적재 완료 - {}", result);
        return result;
    }
}
