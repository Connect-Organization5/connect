package connect.hub_service.hub;

import connect.hub_service.hub.repository.HubRepository;
import connect.hub_service.hub.seed.HubSeedData;
import connect.hub_service.hub.seed.HubSeedLoader;
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
@SpringBootTest
@AutoConfigureMockMvc
class HubApiIntegrationTest {

    private static final UUID SEOUL = HubSeedData.hubs().get(0).id();

    @Autowired
    MockMvc mockMvc;
    @Autowired
    HubSeedLoader hubSeedLoader;
    @Autowired
    HubRepository hubRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    // ---------- 초기 적재 ----------

    @Test
    void 허브_17개가_적재되고_재적재해도_늘지_않는다() {
        hubSeedLoader.load();
        HubSeedLoader.Result again = hubSeedLoader.load();

        assertThat(hubRepository.count()).isEqualTo(17);
        assertThat(again.hubsInserted()).isZero();
        assertThat(again.hubsSkipped()).isEqualTo(17);
    }

    @Test
    void 이미_있는_허브는_재적재해도_덮어쓰지_않는다() {
        String original = jdbcTemplate.queryForObject("select address from p_hubs where id = ?", String.class, SEOUL);
        try {
            jdbcTemplate.update("update p_hubs set address = ? where id = ?", "변경된 주소", SEOUL);

            hubSeedLoader.load();

            assertThat(jdbcTemplate.queryForObject(
                    "select address from p_hubs where id = ?", String.class, SEOUL)).isEqualTo("변경된 주소");
        } finally {
            jdbcTemplate.update("update p_hubs set address = ? where id = ?", original, SEOUL);
        }
    }

    // ---------- 허브 목록 ----------

    @Test
    void 허브_목록은_ApiResponse로_감싸고_페이지_정보를_반환한다() throws Exception {
        mockMvc.perform(get("/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(17))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].hubId").exists())
                .andExpect(jsonPath("$.data.content[0].name").exists())
                .andExpect(jsonPath("$.data.content[0].address").exists());
    }

    @Test
    void 두번째_페이지는_나머지_7개다() throws Exception {
        mockMvc.perform(get("/hubs").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(7));
    }

    @Test
    void 허브명_또는_주소로_검색한다() throws Exception {
        mockMvc.perform(get("/hubs").param("keyword", "부산"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("부산광역시 센터"));

        mockMvc.perform(get("/hubs").param("keyword", "송파대로"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("서울특별시 센터"));
    }

    @Test
    void 검색_결과가_없으면_빈_목록과_페이지_정보를_반환한다() throws Exception {
        mockMvc.perform(get("/hubs").param("keyword", "존재하지않는검색어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void 와일드카드_문자는_문자_그대로_검색한다() throws Exception {
        mockMvc.perform(get("/hubs").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 잘못된_페이지_값은_400이다() throws Exception {
        mockMvc.perform(get("/hubs").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/hubs").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/hubs").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/hubs").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 허브 단건 ----------

    @Test
    void 허브_단건_조회() throws Exception {
        mockMvc.perform(get("/hubs/{id}", SEOUL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hubId").value(SEOUL.toString()))
                .andExpect(jsonPath("$.data.name").value("서울특별시 센터"))
                .andExpect(jsonPath("$.data.address").value("서울특별시 송파구 송파대로 55"));
    }

    @Test
    void 없는_허브는_404이고_UUID_형식_오류는_400이다() throws Exception {
        mockMvc.perform(get("/hubs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("허브를 찾을 수 없습니다."));

        mockMvc.perform(get("/hubs/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
