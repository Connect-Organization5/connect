package connect.hub_service.hub.seed;

import connect.hub_service.HubServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// hub.seed.enabled 토글 검증. 실제 PostgreSQL이 필요하다. (POSTGRES_USER/POSTGRES_PASSWORD)
// 앱을 직접 기동하므로 @SpringBootTest 대신 SpringApplicationBuilder를 쓴다.
class HubDataInitializerToggleTest {

    private static final String HUBS_SQL = "select id, name, address from p_hubs order by id";
    private static final String ROUTES_SQL = "select id, departure_hub_id, arrival_hub_id, distance, duration from p_hub_routes order by id";

    private ConfigurableApplicationContext start(String seedEnabled) {
        return new SpringApplicationBuilder(HubServiceApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                // properties()는 application.yml보다 우선순위가 낮아 실행 인자로 지정한다.
                .run("--hub.seed.enabled=" + seedEnabled, "--eureka.client.enabled=false");
    }

    @Test
    void false이면_초기화기_빈이_없고_기존_데이터는_변하지_않는다() {
        // 전제 데이터를 명시적으로 준비한다 (기본값 true 컨텍스트의 적재 결과에 의존하지 않음)
        try (ConfigurableApplicationContext ctx = start("true")) {
            ctx.getBean(HubSeedLoader.class).load();
            ctx.getBean(HubRouteSeedLoader.class).load();
        }

        List<Map<String, Object>> hubsBefore;
        List<Map<String, Object>> routesBefore;
        try (ConfigurableApplicationContext ctx = start("false")) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            hubsBefore = jdbc.queryForList(HUBS_SQL);
            routesBefore = jdbc.queryForList(ROUTES_SQL);
            assertThat(hubsBefore).hasSize(17);
            assertThat(routesBefore).hasSize(272);

            assertThat(ctx.getBeansOfType(HubDataInitializer.class)).isEmpty();
            // 로더 빈은 그대로 존재한다(호출만 하지 않음)
            assertThat(ctx.getBeansOfType(HubSeedLoader.class)).hasSize(1);
            assertThat(ctx.getBeansOfType(HubRouteSeedLoader.class)).hasSize(1);
        }

        // false로 기동한 뒤에도 데이터 값이 그대로다.
        try (ConfigurableApplicationContext ctx = start("false")) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForList(HUBS_SQL)).isEqualTo(hubsBefore);
            assertThat(jdbc.queryForList(ROUTES_SQL)).isEqualTo(routesBefore);
        }
    }

    @Test
    void false로_기동하면_삭제된_경로를_다시_적재하지_않는다() {
        String routeId;
        Map<String, Object> deleted;
        try (ConfigurableApplicationContext ctx = start("true")) {
            ctx.getBean(HubSeedLoader.class).load();
            ctx.getBean(HubRouteSeedLoader.class).load();
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            deleted = jdbc.queryForList("select * from p_hub_routes limit 1").get(0);
            routeId = deleted.get("id").toString();
            jdbc.update("delete from p_hub_routes where id = ?::uuid", routeId);
        }

        try {
            // false: 빠진 경로를 되살리지 않는다 = 적재가 실제로 수행되지 않는다
            try (ConfigurableApplicationContext ctx = start("false")) {
                Integer count = ctx.getBean(JdbcTemplate.class).queryForObject("select count(*) from p_hub_routes", Integer.class);
                assertThat(count).isEqualTo(271);
            }
            // true(기본): 없는 것만 다시 적재한다
            try (ConfigurableApplicationContext ctx = start("true")) {
                Integer count = ctx.getBean(JdbcTemplate.class).queryForObject("select count(*) from p_hub_routes", Integer.class);
                assertThat(count).isEqualTo(272);
            }
        } finally {
            try (ConfigurableApplicationContext ctx = start("true")) {
                ctx.getBean(HubRouteSeedLoader.class).load();
            }
        }
    }

    @Test
    void true로_반복_기동해도_건수와_출발_도착_쌍이_중복되지_않는다() {
        for (int i = 0; i < 2; i++) {
            try (ConfigurableApplicationContext ctx = start("true")) {
                JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("select count(*) from p_hubs", Integer.class)).isEqualTo(17);
                assertThat(jdbc.queryForObject("select count(*) from p_hub_routes", Integer.class)).isEqualTo(272);
                assertThat(jdbc.queryForObject(
                        "select count(*) from (select departure_hub_id, arrival_hub_id from p_hub_routes "
                                + "group by 1, 2 having count(*) > 1) d", Integer.class)).isZero();
            }
        }
    }
}
