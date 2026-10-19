package connect.hub_service.hub.seed;

import connect.hub_service.hub.seed.HubSeedData.HubSeed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HubSeedDataTest {

    @Test
    void 허브는_17개이고_ID와_이름이_중복되지_않는다() {
        List<HubSeed> hubs = HubSeedData.hubs();

        assertThat(hubs).hasSize(17);
        assertThat(hubs.stream().map(HubSeed::id).collect(Collectors.toSet())).hasSize(17);
        assertThat(hubs.stream().map(HubSeed::name).collect(Collectors.toSet())).hasSize(17);
    }

    @Test
    void 이름과_주소가_비어있지_않고_컬럼_길이_안이다() {
        assertThat(HubSeedData.hubs()).allSatisfy(h -> {
            assertThat(h.name()).isNotBlank().hasSizeLessThanOrEqualTo(100);
            assertThat(h.address()).isNotBlank().hasSizeLessThanOrEqualTo(200);
        });
    }

    @Test
    void ID는_이름_기반이라_여러_번_생성해도_같다() {
        assertThat(HubSeedData.hubs()).isEqualTo(HubSeedData.hubs());
        assertThat(HubSeedData.hubs().get(0).id().toString()).isEqualTo("c16301c4-6f1f-3ec3-be8d-2b9d85642632");
    }
}
