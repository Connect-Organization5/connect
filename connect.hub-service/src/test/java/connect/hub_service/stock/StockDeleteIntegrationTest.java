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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 재고 일괄 논리 삭제(POST /internal/hubs/stocks/delete) 검증. 실제 PostgreSQL이 필요하다.
// 업체 삭제·개별 상품 삭제 모두 Company가 productIds를 모아 이 API를 호출한다. 삭제자는 X-User-Id로 받아 deleted_by에 기록한다.
@SpringBootTest
@AutoConfigureMockMvc
class StockDeleteIntegrationTest {

    private static final UUID SEOUL = HubSeedData.hubs().get(0).id();
    private static final UUID BUSAN = HubSeedData.hubs().get(3).id();
    private static final UUID DAEJEON = HubSeedData.hubs().get(2).id();

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
            jdbcTemplate.update(
                    "delete from p_stock_histories where stock_id in (select id from p_stocks where product_id = ?)",
                    productId);
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

    private UUID deletedByOf(UUID stockId) {
        return jdbcTemplate.queryForObject("select deleted_by from p_stocks where id = ?", UUID.class, stockId);
    }

    private Timestamp deletedAtOf(UUID stockId) {
        return jdbcTemplate.queryForObject("select deleted_at from p_stocks where id = ?", Timestamp.class, stockId);
    }

    private static String idsJson(UUID... ids) {
        return "{\"productIds\": [" + java.util.Arrays.stream(ids)
                .map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }

    private ResultActions delete(String userId, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/internal/hubs/stocks/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        return mockMvc.perform(request);
    }

    private ResultActions delete(UUID userId, UUID... productIds) throws Exception {
        return delete(userId.toString(), idsJson(productIds));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- 삭제 ----

    @Test
    void 상품이_등록된_모든_허브의_재고를_삭제하고_삭제자를_기록하며_수량은_유지한다() throws Exception {
        UUID productId = UUID.randomUUID();
        TestStock seoul = createStock(SEOUL, productId, 100);
        TestStock busan = createStock(BUSAN, productId, 50);
        TestStock daejeon = createStock(DAEJEON, productId, 30);
        UUID userId = UUID.randomUUID();

        delete(userId, productId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(3));

        for (TestStock stock : List.of(seoul, busan, daejeon)) {
            assertThat(deletedByOf(stock.id())).isEqualTo(userId);
            assertThat(deletedAtOf(stock.id())).isNotNull();
        }
        // 같은 호출의 재고는 모두 같은 삭제 시각이다.
        assertThat(deletedAtOf(seoul.id())).isEqualTo(deletedAtOf(busan.id())).isEqualTo(deletedAtOf(daejeon.id()));
        assertThat(quantityOf(seoul.id())).isEqualTo(100);
        assertThat(quantityOf(busan.id())).isEqualTo(50);
        assertThat(quantityOf(daejeon.id())).isEqualTo(30);
    }

    @Test
    void 여러_상품을_한_번에_삭제하고_다른_상품의_재고는_건드리지_않는다() throws Exception {
        TestStock first = createStock(10);
        TestStock second = createStock(20);
        TestStock untouched = createStock(30);
        UUID userId = UUID.randomUUID();

        delete(userId, first.productId(), second.productId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2));

        assertThat(deletedAtOf(first.id())).isNotNull();
        assertThat(deletedAtOf(second.id())).isNotNull();
        assertThat(deletedAtOf(untouched.id())).isNull();
        assertThat(deletedByOf(untouched.id())).isNull();
    }

    @Test
    void 중복된_상품_ID는_한_번만_처리한다() throws Exception {
        TestStock stock = createStock(10);

        delete(UUID.randomUUID(), stock.productId(), stock.productId(), stock.productId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1));
    }

    @Test
    void 이미_삭제된_재고는_건너뛰고_deletedBy와_삭제_시각을_바꾸지_않는다() throws Exception {
        UUID productId = UUID.randomUUID();
        TestStock alreadyDeleted = createStock(SEOUL, productId, 100);
        TestStock active = createStock(BUSAN, productId, 50);
        UUID firstUser = UUID.randomUUID();
        jdbcTemplate.update("update p_stocks set deleted_at = '2026-10-01 09:00:00', deleted_by = ? where id = ?",
                firstUser, alreadyDeleted.id());
        Timestamp firstDeletedAt = deletedAtOf(alreadyDeleted.id());
        UUID secondUser = UUID.randomUUID();

        delete(secondUser, productId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1));

        assertThat(deletedByOf(alreadyDeleted.id())).isEqualTo(firstUser);
        assertThat(deletedAtOf(alreadyDeleted.id())).isEqualTo(firstDeletedAt);
        assertThat(deletedByOf(active.id())).isEqualTo(secondUser);
    }

    @Test
    void 같은_삭제_요청을_다시_보내면_추가_변경_없이_deletedCount_0으로_성공한다() throws Exception {
        TestStock stock = createStock(10);
        UUID firstUser = UUID.randomUUID();
        delete(firstUser, stock.productId()).andExpect(jsonPath("$.data.deletedCount").value(1));
        Timestamp deletedAt = deletedAtOf(stock.id());

        delete(UUID.randomUUID(), stock.productId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(0));

        assertThat(deletedByOf(stock.id())).isEqualTo(firstUser);
        assertThat(deletedAtOf(stock.id())).isEqualTo(deletedAt);
    }

    @Test
    void Hub에_재고가_없는_상품이어도_404가_아니라_deletedCount_0으로_성공한다() throws Exception {
        delete(UUID.randomUUID(), UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(0));
    }

    @Test
    void 삭제된_재고는_일반_조회에서_제외된다() throws Exception {
        TestStock stock = createStock(10);
        mockMvc.perform(get("/hubs/stocks/{stockId}", stock.id())).andExpect(status().isOk());

        delete(UUID.randomUUID(), stock.productId()).andExpect(status().isOk());

        mockMvc.perform(get("/hubs/stocks/{stockId}", stock.id())).andExpect(status().isNotFound());
        mockMvc.perform(get("/hubs/stocks").param("productId", stock.productId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    // ---- 입력 검증 ----

    @Test
    void X_User_Id가_없으면_400이고_삭제하지_않는다() throws Exception {
        TestStock stock = createStock(10);

        delete(null, idsJson(stock.productId())).andExpect(status().isBadRequest());

        assertThat(deletedAtOf(stock.id())).isNull();
    }

    @Test
    void X_User_Id가_UUID_형식이_아니거나_비어_있으면_400이다() throws Exception {
        TestStock stock = createStock(10);

        for (String userId : new String[]{"not-a-uuid", "", " ", "   "}) {
            delete(userId, idsJson(stock.productId())).andExpect(status().isBadRequest());
        }

        assertThat(deletedAtOf(stock.id())).isNull();
    }

    @Test
    void productIds가_비었거나_null이거나_없으면_400이다() throws Exception {
        UUID userId = UUID.randomUUID();

        delete(userId.toString(), "{\"productIds\": []}").andExpect(status().isBadRequest());
        delete(userId.toString(), "{\"productIds\": null}").andExpect(status().isBadRequest());
        delete(userId.toString(), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void productIds에_null이나_UUID가_아닌_값이_있으면_400이고_삭제하지_않는다() throws Exception {
        TestStock stock = createStock(10);
        UUID userId = UUID.randomUUID();

        delete(userId.toString(), "{\"productIds\": [\"" + stock.productId() + "\", null]}")
                .andExpect(status().isBadRequest());
        delete(userId.toString(), "{\"productIds\": [\"" + stock.productId() + "\", \"not-a-uuid\"]}")
                .andExpect(status().isBadRequest());

        assertThat(deletedAtOf(stock.id())).isNull();
    }

    // ---- 삭제와 주문 처리 ----

    @Test
    void 차감_복원_이력_조회는_X_User_Id_없이_호출된다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();

        postJson("/internal/hubs/stocks/decrease", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s", "quantity": 10}
                """.formatted(orderId, stock.hubId(), stock.productId())).andExpect(status().isOk());
        postJson("/internal/hubs/stocks/restore", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s"}
                """.formatted(orderId, stock.hubId(), stock.productId())).andExpect(status().isOk());
        mockMvc.perform(get("/internal/hubs/stocks/histories").param("orderId", orderId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void 차감_후_삭제하고_주문이_취소되면_수량은_복원되고_삭제_상태는_유지된다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        postJson("/internal/hubs/stocks/decrease", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s", "quantity": 10}
                """.formatted(orderId, stock.hubId(), stock.productId())).andExpect(status().isOk());

        delete(userId, stock.productId()).andExpect(jsonPath("$.data.deletedCount").value(1));
        assertThat(quantityOf(stock.id())).isEqualTo(90);
        Timestamp deletedAt = deletedAtOf(stock.id());

        postJson("/internal/hubs/stocks/restore", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s"}
                """.formatted(orderId, stock.hubId(), stock.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentQuantity").value(100));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(deletedAtOf(stock.id())).isEqualTo(deletedAt);
        assertThat(deletedByOf(stock.id())).isEqualTo(userId);
        mockMvc.perform(get("/hubs/stocks/{stockId}", stock.id())).andExpect(status().isNotFound());
        // 삭제된 재고에는 새 주문을 차감할 수 없다.
        postJson("/internal/hubs/stocks/decrease", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s", "quantity": 1}
                """.formatted(UUID.randomUUID(), stock.hubId(), stock.productId())).andExpect(status().isConflict());
    }

    @Test
    void 삭제와_차감이_동시에_들어와도_수량과_이력이_일치한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<Callable<Integer>> tasks = List.of(
                () -> delete(userId, stock.productId()).andReturn().getResponse().getStatus(),
                () -> postJson("/internal/hubs/stocks/decrease", """
                        {"orderId": "%s", "hubId": "%s", "productId": "%s", "quantity": 10}
                        """.formatted(orderId, stock.hubId(), stock.productId())).andReturn().getResponse().getStatus());

        List<Integer> statuses = runConcurrently(tasks);

        // 삭제는 항상 성공한다. 차감은 삭제보다 먼저면 200, 늦으면 삭제된 재고라 409다.
        assertThat(statuses.get(0)).isEqualTo(200);
        assertThat(statuses.get(1)).isIn(200, 409);
        assertThat(deletedByOf(stock.id())).isEqualTo(userId);
        int historyCount = jdbcTemplate.queryForObject(
                "select count(*) from p_stock_histories where order_id = ?", Integer.class, orderId);
        if (statuses.get(1) == 200) {
            assertThat(quantityOf(stock.id())).isEqualTo(90);
            assertThat(historyCount).isEqualTo(1);
        } else {
            assertThat(quantityOf(stock.id())).isEqualTo(100);
            assertThat(historyCount).isZero();
        }
    }

    @Test
    void 같은_삭제_요청이_동시에_들어와도_한_번만_삭제한다() throws Exception {
        TestStock stock = createStock(100);
        UUID userId = UUID.randomUUID();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> delete(userId, stock.productId()).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(deletedByOf(stock.id())).isEqualTo(userId);
        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();
        try {
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return statuses;
    }
}
