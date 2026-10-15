package connect.hub_service.hub.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 앱 기동 시 초기 데이터를 적재한다. 재시작해도 중복 생성·덮어쓰기가 없다.
@Slf4j
@Component
@RequiredArgsConstructor
public class HubDataInitializer implements ApplicationRunner {

    private final HubSeedLoader hubSeedLoader;

    @Override
    public void run(ApplicationArguments args) {
        try {
            hubSeedLoader.load();
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 동시에 같은 데이터를 적재한 경우. 기존 데이터를 유지하고 다음 기동에서 보완된다.
            log.warn("허브 초기 데이터 적재 중 중복이 감지되어 건너뜁니다: {}", e.getMostSpecificCause().getMessage());
        }
    }
}
