package connect.hub_service.stock;

import connect.hub_service.hub.seed.HubSeedData;
import connect.hub_service.hub.seed.HubSeedLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 재고 입고(수량 추가) 검증. 요청 본문의 hubId·productId로 재고를 찾는다. 실제 PostgreSQL이 필요하다.
// 테스트가 만든 재고는 상품 ID로 추적해 종료 시 삭제한다.
@SpringBootTest
@AutoConfigureMockMvc
class StockInboundIntegrationTest {

    private static final UUID SEOUL = HubSeedData.hubs().get(0).id();
    private static final UUID BUSAN = HubSeedData.hubs().get(3).id();

    private record TestStock(UUID id, UUID hubId, UUID productId) {
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    HubSeedLoader hubSeedLoader;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private final List<UUID> createdProducts = new ArrayList<>();

    @BeforeEach
    void seedHubs() {
        hubSeedLoader.load();
    }

    @AfterEach
    void cleanUp() {
        for (UUID productId : createdProducts) {
            jdbcTemplate.update("delete from p_stocks where product_id = ?", productId);
        }
        createdProducts.clear();
    }

    private TestStock createStock(UUID hubId, UUID productId, int quantity) {
        UUID stockId = UUID.randomUUID();
        createdProducts.add(productId);
        jdbcTemplate.update("insert into p_stocks (id, hub_id, product_id, quantity) values (?, ?, ?, ?)",
                stockId, hubId, productId, quantity);
        return new TestStock(stockId, hubId, productId);
    }

    private TestStock createStock(int quantity) {
        return createStock(SEOUL, UUID.randomUUID(), quantity);
    }

    private int quantityOf(UUID stockId) {
        return jdbcTemplate.queryForObject("select quantity from p_stocks where id = ?", Integer.class, stockId);
    }

    private ResultActions inbound(String role, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/hubs/stocks/inbound")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    private static String body(Object hubId, Object productId, String quantityJson) {
        return "{\"hubId\":" + json(hubId) + ",\"productId\":" + json(productId) + ",\"quantity\":" + quantityJson + "}";
    }

    private static String json(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions inbound(String role, TestStock stock, String quantityJson) throws Exception {
        return inbound(role, body(stock.hubId(), stock.productId(), quantityJson));
    }

    @Test
    void MASTER는_기존_재고에_수량을_더한다() throws Exception {
        TestStock stock = createStock(100);

        inbound("MASTER", stock, "50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockId").value(stock.id().toString()))
                .andExpect(jsonPath("$.data.hubId").value(SEOUL.toString()))
                .andExpect(jsonPath("$.data.productId").value(stock.productId().toString()))
                .andExpect(jsonPath("$.data.quantity").value(150));

        assertThat(quantityOf(stock.id())).isEqualTo(150);
        // 조회 API에서도 합산된 수량이 보인다
        mockMvc.perform(get("/hubs/stocks/{id}", stock.id()))
                .andExpect(jsonPath("$.data.quantity").value(150));
    }

    @Test
    void 수량_0인_재고에도_입고할_수_있다() throws Exception {
        TestStock stock = createStock(0);

        inbound("MASTER", stock, "7").andExpect(status().isOk()).andExpect(jsonPath("$.data.quantity").value(7));
    }

    @Test
    void 같은_상품이라도_다른_허브_재고는_영향받지_않는다() throws Exception {
        UUID product = UUID.randomUUID();
        TestStock seoul = createStock(SEOUL, product, 10);
        TestStock busan = createStock(BUSAN, product, 20);

        inbound("MASTER", seoul, "5").andExpect(status().isOk());

        assertThat(quantityOf(seoul.id())).isEqualTo(15);
        assertThat(quantityOf(busan.id())).isEqualTo(20);
    }

    @Test
    void 같은_요청을_다시_보내면_수량이_중복_증가한다_멱등하지_않음() throws Exception {
        TestStock stock = createStock(100);

        inbound("MASTER", stock, "50").andExpect(status().isOk());
        inbound("MASTER", stock, "50").andExpect(status().isOk());

        assertThat(quantityOf(stock.id())).isEqualTo(200);
    }

    @Test
    void MASTER가_아니면_403이고_수량은_그대로다() throws Exception {
        TestStock stock = createStock(100);

        inbound("USER", stock, "50").andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 역할_헤더가_없으면_400이다() throws Exception {
        TestStock stock = createStock(100);

        inbound(null, stock, "50").andExpect(status().isBadRequest());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 허브_상품_조합의_재고가_없으면_404이다() throws Exception {
        inbound("MASTER", body(SEOUL, UUID.randomUUID(), "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("재고를 찾을 수 없습니다."));
    }

    @Test
    void 다른_허브에만_있는_상품이면_404이다() throws Exception {
        TestStock busan = createStock(BUSAN, UUID.randomUUID(), 10);

        inbound("MASTER", body(SEOUL, busan.productId(), "5")).andExpect(status().isNotFound());

        assertThat(quantityOf(busan.id())).isEqualTo(10);
    }

    @Test
    void 없는_허브는_404이다() throws Exception {
        inbound("MASTER", body(UUID.randomUUID(), UUID.randomUUID(), "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("허브를 찾을 수 없습니다."));
    }

    @Test
    void 논리_삭제된_재고는_409이고_수량은_그대로다() throws Exception {
        TestStock stock = createStock(100);
        jdbcTemplate.update("update p_stocks set deleted_at = now() where id = ?", stock.id());

        inbound("MASTER", stock, "5")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("삭제된 재고입니다."));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 입고_수량이_0_음수_누락이거나_형식이_틀리면_400이다() throws Exception {
        TestStock stock = createStock(100);

        inbound("MASTER", stock, "0").andExpect(status().isBadRequest());
        inbound("MASTER", stock, "-5").andExpect(status().isBadRequest());
        inbound("MASTER", stock, "null").andExpect(status().isBadRequest());
        inbound("MASTER", stock, "\"abc\"").andExpect(status().isBadRequest());
        inbound("MASTER", stock, "99999999999").andExpect(status().isBadRequest());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 허브_상품_ID가_없거나_형식이_틀리면_400이다() throws Exception {
        TestStock stock = createStock(100);

        inbound("MASTER", body(null, stock.productId(), "5")).andExpect(status().isBadRequest());
        inbound("MASTER", body(SEOUL, null, "5")).andExpect(status().isBadRequest());
        inbound("MASTER", body("not-a-uuid", stock.productId(), "5")).andExpect(status().isBadRequest());
        inbound("MASTER", body(SEOUL, "not-a-uuid", "5")).andExpect(status().isBadRequest());
        inbound("MASTER", "{}").andExpect(status().isBadRequest());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 합산이_int_범위를_넘으면_409이고_수량은_그대로다() throws Exception {
        TestStock stock = createStock(Integer.MAX_VALUE - 5);

        inbound("MASTER", stock, "6")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("재고 수량이 허용 범위를 초과합니다."));
        assertThat(quantityOf(stock.id())).isEqualTo(Integer.MAX_VALUE - 5);

        // 정확히 한계까지는 가능하다
        inbound("MASTER", stock, "5").andExpect(status().isOk());
        assertThat(quantityOf(stock.id())).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void 입고_URL이_재고_단건_조회와_충돌하지_않는다() throws Exception {
        TestStock stock = createStock(10);

        mockMvc.perform(get("/hubs/stocks/{id}", stock.id())).andExpect(status().isOk());
        inbound("MASTER", stock, "1").andExpect(status().isOk());
    }

    @Test
    void 동시에_입고해도_증가분이_유실되지_않는다() throws Exception {
        TestStock stock = createStock(0);
        int threads = 10;
        int perRequest = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return inbound("MASTER", stock, String.valueOf(perRequest))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(quantityOf(stock.id())).isEqualTo(threads * perRequest);
    }
}
