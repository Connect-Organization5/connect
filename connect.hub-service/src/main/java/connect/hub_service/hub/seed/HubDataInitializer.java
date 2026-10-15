package connect.hub_service.hub.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 초기 데이터를 적재한다. 재시작해도 중복 생성·덮어쓰기가 없다.
 *
 * <p>hub.seed.enabled(환경변수 HUB_SEED_ENABLED, 기본 true)가 false이면 이 빈이 생성되지 않아 적재를 수행하지 않는다.
 * false는 기존 데이터를 삭제하거나 비활성화하지 않는다. 허브·경로는 고정 기준 데이터이므로 운영에서는 적재 완료 후
 * false로 두는 것을 권장하며, 운영 전환 시 마이그레이션 도구(Flyway) 도입을 검토한다.
 */
@ConditionalOnProperty(name = "hub.seed.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class HubDataInitializer implements ApplicationRunner {

    private final HubSeedLoader hubSeedLoader;
    private final HubRouteSeedLoader hubRouteSeedLoader;

    @Override
    public void run(ApplicationArguments args) {
        try {
            hubSeedLoader.load();
            // 경로는 허브가 적재된 뒤에 적재한다. 거리·시간은 대표 좌표 기반 추정값이다.
            hubRouteSeedLoader.load();
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 동시에 같은 데이터를 적재한 경우. 기존 데이터를 유지하고 다음 기동에서 보완된다.
            log.warn("초기 데이터 적재 중 중복이 감지되어 건너뜁니다: {}", e.getMostSpecificCause().getMessage());
        }
    }
}
