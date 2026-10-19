package connect.hub_service.hub;

import connect.hub_service.hub.repository.HubRouteRepository;
import connect.hub_service.hub.seed.HubDataInitializer;
import connect.hub_service.hub.seed.HubRouteSeedData;
import connect.hub_service.hub.seed.HubRouteSeedLoader;
import connect.hub_service.hub.seed.HubSeedData;
import connect.hub_service.hub.seed.HubSeedLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 PostgreSQL에 연결해 실행한다. POSTGRES_USER/POSTGRES_PASSWORD 환경변수가 필요하다.
// distance/duration은 대표 좌표 기반 추정값이다.
@SpringBootTest
@AutoConfigureMockMvc
class HubRouteApiIntegrationTest {

    private static final UUID SEOUL = HubSeedData.hubs().get(0).id();
    private static final UUID BUSAN = HubSeedData.hubs().get(3).id();

    @Autowired
    MockMvc mockMvc;
    @Autowired
    HubSeedLoader hubSeedLoader;
    @Autowired
    HubRouteSeedLoader hubRouteSeedLoader;
    @Autowired
    HubRouteRepository hubRouteRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    HubDataInitializer hubDataInitializer;

    @BeforeEach
    void seed() {
        hubSeedLoader.load();
        hubRouteSeedLoader.load();
    }

    // ---------- 초기 적재 ----------

    @Test
    void 경로_272개가_적재되고_재적재해도_늘지_않는다() {
        HubRouteSeedLoader.Result again = hubRouteSeedLoader.load();

        assertThat(hubRouteRepository.count()).isEqualTo(272);
        assertThat(again.routesInserted()).isZero();
        assertThat(again.routesSkipped()).isEqualTo(272);
    }

    @Test
    void 기동_시_초기화기를_다시_실행해도_건수가_유지된다() {
        hubDataInitializer.run(null);

        assertThat(hubRouteRepository.count()).isEqualTo(272);
    }

    @Test
    void 이미_있는_경로는_재적재해도_덮어쓰지_않는다() {
        Double originalDistance = jdbcTemplate.queryForObject(
                "select distance from p_hub_routes where departure_hub_id = ? and arrival_hub_id = ?",
                Double.class, SEOUL, BUSAN);
        try {
            jdbcTemplate.update(
                    "update p_hub_routes set distance = 1.5 where departure_hub_id = ? and arrival_hub_id = ?", SEOUL, BUSAN);

            hubRouteSeedLoader.load();

            assertThat(jdbcTemplate.queryForObject(
                    "select distance from p_hub_routes where departure_hub_id = ? and arrival_hub_id = ?",
                    Double.class, SEOUL, BUSAN)).isEqualTo(1.5);
        } finally {
            jdbcTemplate.update(
                    "update p_hub_routes set distance = ? where departure_hub_id = ? and arrival_hub_id = ?",
                    originalDistance, SEOUL, BUSAN);
        }
    }

    @Test
    void 출발_도착_쌍_UNIQUE_제약이_DB에_있다() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conrelid = 'p_hub_routes'::regclass and contype = 'u'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    // ---------- 목록 ----------

    @Test
    void 경로_목록은_ApiResponse로_감싸고_페이지_정보를_반환한다() throws Exception {
        mockMvc.perform(get("/hubs/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(272))
                .andExpect(jsonPath("$.data.totalPages").value(28))
                .andExpect(jsonPath("$.data.content[0].routeId").exists())
                .andExpect(jsonPath("$.data.content[0].departureHubId").exists())
                .andExpect(jsonPath("$.data.content[0].arrivalHubId").exists())
                .andExpect(jsonPath("$.data.content[0].distance").exists())
                .andExpect(jsonPath("$.data.content[0].duration").exists());
    }

    @Test
    void 출발_허브로_필터링하면_나머지_16개_허브로의_경로다() throws Exception {
        mockMvc.perform(get("/hubs/routes").param("departureHubId", SEOUL.toString()).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(16))
                .andExpect(jsonPath("$.data.content[?(@.departureHubId != '" + SEOUL + "')]").isEmpty());
    }

    @Test
    void 도착_허브로_필터링한다() throws Exception {
        mockMvc.perform(get("/hubs/routes").param("arrivalHubId", BUSAN.toString()).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(16))
                .andExpect(jsonPath("$.data.content[?(@.arrivalHubId != '" + BUSAN + "')]").isEmpty());
    }

    @Test
    void 출발과_도착을_모두_주면_AND로_한_건이다() throws Exception {
        mockMvc.perform(get("/hubs/routes")
                        .param("departureHubId", SEOUL.toString())
                        .param("arrivalHubId", BUSAN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].departureHubId").value(SEOUL.toString()))
                .andExpect(jsonPath("$.data.content[0].arrivalHubId").value(BUSAN.toString()));
    }

    @Test
    void 존재하지_않는_허브로_필터링하면_빈_목록이다() throws Exception {
        mockMvc.perform(get("/hubs/routes").param("departureHubId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 잘못된_페이지_값과_UUID_형식은_400이다() throws Exception {
        mockMvc.perform(get("/hubs/routes").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/hubs/routes").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/hubs/routes").param("departureHubId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 단건 ----------

    @Test
    void 경로_단건_조회() throws Exception {
        HubRouteSeedData.RouteSeed expected = HubRouteSeedData.routes().stream()
                .filter(r -> r.departureHubId().equals(SEOUL) && r.arrivalHubId().equals(BUSAN))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/hubs/routes/{dep}/{arr}", SEOUL, BUSAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.routeId").value(expected.id().toString()))
                .andExpect(jsonPath("$.data.departureHubId").value(SEOUL.toString()))
                .andExpect(jsonPath("$.data.arrivalHubId").value(BUSAN.toString()))
                .andExpect(jsonPath("$.data.distance").value(expected.distance()))
                .andExpect(jsonPath("$.data.duration").value(expected.duration()));
    }

    @Test
    void 반대_방향_경로도_같은_거리와_시간이지만_다른_경로_ID다() throws Exception {
        HubRouteSeedData.RouteSeed forward = find(SEOUL, BUSAN);
        HubRouteSeedData.RouteSeed backward = find(BUSAN, SEOUL);

        mockMvc.perform(get("/hubs/routes/{dep}/{arr}", BUSAN, SEOUL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value(backward.id().toString()))
                .andExpect(jsonPath("$.data.departureHubId").value(BUSAN.toString()))
                .andExpect(jsonPath("$.data.distance").value(forward.distance()))
                .andExpect(jsonPath("$.data.duration").value(forward.duration()));
        assertThat(backward.id()).isNotEqualTo(forward.id());
    }

    private static HubRouteSeedData.RouteSeed find(UUID dep, UUID arr) {
        return HubRouteSeedData.routes().stream()
                .filter(r -> r.departureHubId().equals(dep) && r.arrivalHubId().equals(arr))
                .findFirst().orElseThrow();
    }

    @Test
    void 없는_경로는_404이고_자기_자신_경로는_없다() throws Exception {
        mockMvc.perform(get("/hubs/routes/{dep}/{arr}", SEOUL, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이동 경로를 찾을 수 없습니다."));

        mockMvc.perform(get("/hubs/routes/{dep}/{arr}", SEOUL, SEOUL))
                .andExpect(status().isNotFound());
    }

    @Test
    void 경로_URL_UUID_형식_오류는_400이다() throws Exception {
        mockMvc.perform(get("/hubs/routes/not-a-uuid/{arr}", BUSAN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 허브_단건_API와_경로가_충돌하지_않는다() throws Exception {
        mockMvc.perform(get("/hubs/{id}", SEOUL)).andExpect(status().isOk());
        mockMvc.perform(get("/hubs/routes")).andExpect(status().isOk());
    }
}
