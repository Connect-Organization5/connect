package connect.hub_service.hub.seed;

import connect.hub_service.hub.entity.Hub;
import connect.hub_service.hub.repository.HubRepository;
import connect.hub_service.hub.seed.HubSeedData.HubSeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 허브 초기 데이터를 "없는 것만" 삽입한다. 이미 있는 행은 조회만 하고 수정하지 않는다.
 * 같은 ID 또는 같은 이름의 허브가 이미 있으면 건너뛴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HubSeedLoader {

    public record Result(int hubsInserted, int hubsSkipped) {
    }

    private final HubRepository hubRepository;

    @Transactional
    public Result load() {
        int inserted = 0;
        int skipped = 0;

        for (HubSeed seed : HubSeedData.hubs()) {
            if (hubRepository.existsById(seed.id()) || hubRepository.findByName(seed.name()).isPresent()) {
                skipped++;
                continue;
            }
            hubRepository.save(new Hub(seed.id(), seed.name(), seed.address()));
            inserted++;
        }

        Result result = new Result(inserted, skipped);
        log.info("허브 초기 데이터 적재 완료 - {}", result);
        return result;
    }
}
