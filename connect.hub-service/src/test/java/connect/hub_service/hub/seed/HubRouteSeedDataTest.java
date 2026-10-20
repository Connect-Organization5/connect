package connect.hub_service.hub.seed;

import connect.hub_service.hub.seed.HubRouteSeedData.Coordinate;
import connect.hub_service.hub.seed.HubRouteSeedData.RouteSeed;
import connect.hub_service.hub.seed.HubSeedData.HubSeed;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// DB 없이 경로 시드 정의(추정값)만 검증한다.
class HubRouteSeedDataTest {

    private final List<RouteSeed> routes = HubRouteSeedData.routes();

    @Test
    void 모든_허브에_대표_좌표가_있다() {
        for (HubSeed hub : HubSeedData.hubs()) {
            assertThat(HubRouteSeedData.coordinate(hub.name())).as(hub.name()).isNotNull();
        }
    }

    @Test
    void 허브_17개의_서로_다른_쌍_136개를_양방향으로_272건_만든다() {
        assertThat(routes).hasSize(272);
    }

    @Test
    void 같은_출발_도착_허브_경로는_없다() {
        assertThat(routes).noneMatch(r -> r.departureHubId().equals(r.arrivalHubId()));
    }

    @Test
    void 출발_도착_쌍과_경로_ID에_중복이_없다() {
        Set<String> pairs = routes.stream()
                .map(r -> r.departureHubId() + ":" + r.arrivalHubId())
                .collect(Collectors.toSet());
        Set<UUID> ids = routes.stream().map(RouteSeed::id).collect(Collectors.toSet());

        assertThat(pairs).hasSize(272);
        assertThat(ids).hasSize(272);
    }

    @Test
    void 모든_경로는_반대_방향_경로가_있고_거리와_시간이_같다() {
        Set<String> pairs = new HashSet<>();
        routes.forEach(r -> pairs.add(r.departureHubId() + ":" + r.arrivalHubId()));

        for (RouteSeed r : routes) {
            assertThat(pairs).contains(r.arrivalHubId() + ":" + r.departureHubId());
            RouteSeed reverse = routes.stream()
                    .filter(o -> o.departureHubId().equals(r.arrivalHubId()) && o.arrivalHubId().equals(r.departureHubId()))
                    .findFirst().orElseThrow();
            assertThat(reverse.distance()).isEqualTo(r.distance());
            assertThat(reverse.duration()).isEqualTo(r.duration());
        }
    }

    @Test
    void 거리와_시간은_양수다() {
        assertThat(routes).allMatch(r -> r.distance() > 0 && r.duration() > 0);
    }

    @Test
    void 출발_도착_허브는_모두_적재_대상_허브다() {
        Set<UUID> hubIds = HubSeedData.hubs().stream().map(HubSeed::id).collect(Collectors.toSet());
        assertThat(routes).allMatch(r -> hubIds.contains(r.departureHubId()) && hubIds.contains(r.arrivalHubId()));
    }

    @Test
    void 거리는_하버사인_직선거리_x_1점3이고_시간은_80kmh_기준이다() {
        Coordinate seoul = HubRouteSeedData.coordinate("서울특별시 센터");
        Coordinate busan = HubRouteSeedData.coordinate("부산광역시 센터");

        double expected = Math.round(HubRouteSeedData.haversineKm(seoul, busan) * 1.3 * 10) / 10.0;
        double distance = HubRouteSeedData.estimateDistance(seoul, busan);

        assertThat(distance).isEqualTo(expected);
        assertThat(HubRouteSeedData.estimateDuration(distance)).isEqualTo((int) Math.round(distance / 80.0 * 60));
        // 서울-부산 직선거리는 대략 320km 안팎이어야 한다 (공식 오류 감지용)
        assertThat(HubRouteSeedData.haversineKm(seoul, busan)).isBetween(310.0, 330.0);
    }

    @Test
    void 알려진_거리_계산_기준값() {
        // 같은 좌표는 거리 0, 경도 1도 차이(적도)는 약 111.2km
        Coordinate a = new Coordinate(0, 0);
        assertThat(HubRouteSeedData.haversineKm(a, a)).isZero();
        assertThat(HubRouteSeedData.haversineKm(a, new Coordinate(0, 1))).isBetween(111.0, 111.4);
        assertThat(HubRouteSeedData.estimateDuration(80.0)).isEqualTo(60);
    }
}
