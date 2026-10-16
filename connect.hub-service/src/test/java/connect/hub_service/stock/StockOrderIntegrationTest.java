package connect.hub_service.stock;

import connect.hub_service.hub.seed.HubSeedData;
import connect.hub_service.hub.seed.HubSeedLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 주문 재고 차감·복원·이력 조회(내부 API) 검증. 실제 PostgreSQL이 필요하다.
// 내부 API는 X-User-* 헤더 없이 호출한다. 테스트가 만든 재고와 이력은 상품 ID로 추적해 종료 시 삭제한다.
@SpringBootTest
@AutoConfigureMockMvc
class StockOrderIntegrationTest {

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

    private int historyCount(UUID orderId) {
        return jdbcTemplate.queryForObject("select count(*) from p_stock_histories where order_id = ?",
                Integer.class, orderId);
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String decreaseBody(UUID orderId, UUID hubId, UUID productId, Object quantity) {
        return """
                {"orderId": "%s", "hubId": "%s", "productId": "%s", "quantity": %s}
                """.formatted(orderId, hubId, productId, quantity);
    }

    private ResultActions decrease(UUID orderId, TestStock stock, int quantity) throws Exception {
        return postJson("/internal/hubs/stocks/decrease", decreaseBody(orderId, stock.hubId(), stock.productId(), quantity));
    }

    private ResultActions restore(UUID orderId, UUID hubId, UUID productId) throws Exception {
        return postJson("/internal/hubs/stocks/restore", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s"}
                """.formatted(orderId, hubId, productId));
    }

    private ResultActions restore(UUID orderId, TestStock stock) throws Exception {
        return restore(orderId, stock.hubId(), stock.productId());
    }

    private ResultActions histories(UUID orderId) throws Exception {
        return mockMvc.perform(get("/internal/hubs/stocks/histories").param("orderId", orderId.toString()));
    }

    // ---- 차감 ----

    @Test
    void 재고를_차감하고_이력을_남긴다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();

        decrease(orderId, stock, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.stockId").value(stock.id().toString()))
                .andExpect(jsonPath("$.data.processedQuantity").value(10))
                .andExpect(jsonPath("$.data.remainingQuantity").value(90))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(90);
        assertThat(jdbcTemplate.queryForObject(
                "select type from p_stock_histories where order_id = ?", String.class, orderId)).isEqualTo("DECREASE");
    }

    @Test
    void 같은_차감_요청을_다시_보내면_재차감하지_않고_alreadyProcessed를_반환한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());

        decrease(orderId, stock, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedQuantity").value(10))
                .andExpect(jsonPath("$.data.remainingQuantity").value(90))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));

        assertThat(quantityOf(stock.id())).isEqualTo(90);
        assertThat(historyCount(orderId)).isEqualTo(1);
    }

    @Test
    void 같은_주문_ID에_다른_수량이면_409이고_수량은_그대로다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());

        decrease(orderId, stock, 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(90);
        assertThat(historyCount(orderId)).isEqualTo(1);
    }

    @Test
    void 같은_주문_ID에_다른_상품이면_409다() throws Exception {
        TestStock first = createStock(100);
        TestStock other = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, first, 10).andExpect(status().isOk());

        decrease(orderId, other, 10).andExpect(status().isConflict());

        assertThat(quantityOf(other.id())).isEqualTo(100);
    }

    @Test
    void 같은_주문_ID에_다른_허브면_409다() throws Exception {
        UUID productId = UUID.randomUUID();
        TestStock seoul = createStock(SEOUL, productId, 100);
        TestStock busan = createStock(BUSAN, productId, 100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, seoul, 10).andExpect(status().isOk());

        decrease(orderId, busan, 10).andExpect(status().isConflict());

        assertThat(quantityOf(busan.id())).isEqualTo(100);
    }

    @Test
    void 재고가_부족하면_409이고_수량과_이력은_바뀌지_않는다() throws Exception {
        TestStock stock = createStock(5);
        UUID orderId = UUID.randomUUID();

        decrease(orderId, stock, 6).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(5);
        assertThat(historyCount(orderId)).isZero();
    }

    @Test
    void 재고를_정확히_0으로_만들_수_있다() throws Exception {
        TestStock stock = createStock(5);

        decrease(UUID.randomUUID(), stock, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingQuantity").value(0));

        assertThat(quantityOf(stock.id())).isZero();
    }

    @Test
    void 재고가_없으면_404다() throws Exception {
        postJson("/internal/hubs/stocks/decrease",
                decreaseBody(UUID.randomUUID(), SEOUL, UUID.randomUUID(), 1))
                .andExpect(status().isNotFound());
    }

    @Test
    void 복원이_끝난_주문의_차감_요청은_재차감하지_않고_409다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        restore(orderId, stock).andExpect(status().isOk());

        decrease(orderId, stock, 10).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 복원이_끝난_주문에_다른_값으로_차감을_요청해도_복원_완료로_409다() throws Exception {
        TestStock stock = createStock(100);
        TestStock other = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        restore(orderId, stock).andExpect(status().isOk());

        // 요청 불일치보다 복원 완료가 우선한다. 메시지로 두 409를 구분한다.
        decrease(orderId, other, 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 복원이 완료된 주문입니다."));
        decrease(orderId, stock, 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 복원이 완료된 주문입니다."));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(quantityOf(other.id())).isEqualTo(100);
    }

    @Test
    void 차감_요청의_필드가_잘못되면_400이다() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        for (Object quantity : new Object[]{0, -1, "null"}) {
            postJson("/internal/hubs/stocks/decrease", decreaseBody(orderId, SEOUL, productId, quantity))
                    .andExpect(status().isBadRequest());
        }
        postJson("/internal/hubs/stocks/decrease", """
                {"hubId": "%s", "productId": "%s", "quantity": 1}
                """.formatted(SEOUL, productId)).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/decrease", """
                {"orderId": "%s", "productId": "%s", "quantity": 1}
                """.formatted(orderId, productId)).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/decrease", """
                {"orderId": "%s", "hubId": "%s", "quantity": 1}
                """.formatted(orderId, SEOUL)).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/decrease", """
                {"orderId": "not-a-uuid", "hubId": "%s", "productId": "%s", "quantity": 1}
                """.formatted(SEOUL, productId)).andExpect(status().isBadRequest());
        assertThat(historyCount(orderId)).isZero();
    }

    // ---- 복원 ----

    @Test
    void 차감한_수량만큼_복원하고_RESTORE_이력을_추가한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.stockId").value(stock.id().toString()))
                .andExpect(jsonPath("$.data.restoredQuantity").value(10))
                .andExpect(jsonPath("$.data.currentQuantity").value(100))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        // 기존 DECREASE는 그대로 두고 RESTORE를 새로 추가한다.
        assertThat(historyCount(orderId)).isEqualTo(2);
    }

    @Test
    void 같은_복원_요청을_다시_보내면_재복원하지_않고_alreadyProcessed를_반환한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        restore(orderId, stock).andExpect(status().isOk());

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredQuantity").value(10))
                .andExpect(jsonPath("$.data.currentQuantity").value(100))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(historyCount(orderId)).isEqualTo(2);
    }

    @Test
    void 원본_차감_이력이_없으면_409다() throws Exception {
        TestStock stock = createStock(100);

        restore(UUID.randomUUID(), stock).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 요청한_허브나_상품이_원본_재고와_다르면_409다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());

        restore(orderId, stock.hubId(), UUID.randomUUID()).andExpect(status().isConflict());
        restore(orderId, BUSAN, stock.productId()).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(90);
        assertThat(historyCount(orderId)).isEqualTo(1);
    }

    @Test
    void 복원으로_수량이_int_범위를_넘으면_409이고_이력을_남기지_않는다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        jdbcTemplate.update("update p_stocks set quantity = ? where id = ?", Integer.MAX_VALUE, stock.id());

        restore(orderId, stock).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(Integer.MAX_VALUE);
        assertThat(historyCount(orderId)).isEqualTo(1);
    }

    @Test
    void 복원_요청의_필드가_없으면_400이다() throws Exception {
        postJson("/internal/hubs/stocks/restore", """
                {"hubId": "%s", "productId": "%s"}
                """.formatted(SEOUL, UUID.randomUUID())).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/restore", """
                {"orderId": "%s", "productId": "%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/restore", """
                {"orderId": "%s", "hubId": "%s"}
                """.formatted(UUID.randomUUID(), SEOUL)).andExpect(status().isBadRequest());
    }

    // ---- 삭제된 재고 ----

    private void markDeleted(UUID stockId, UUID actorId) {
        jdbcTemplate.update("update p_stocks set deleted_at = now(), deleted_by = ? where id = ?", actorId, stockId);
    }

    @Test
    void 삭제된_재고에_신규_차감하면_409이고_수량과_이력은_바뀌지_않는다() throws Exception {
        TestStock stock = createStock(100);
        markDeleted(stock.id(), UUID.randomUUID());
        UUID orderId = UUID.randomUUID();

        decrease(orderId, stock, 10).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(historyCount(orderId)).isZero();
    }

    @Test
    void 삭제된_재고도_이전_주문이_취소되면_수량을_복원하고_삭제_상태는_유지한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        markDeleted(stock.id(), actorId);

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredQuantity").value(10))
                .andExpect(jsonPath("$.data.currentQuantity").value(100))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(jdbcTemplate.queryForObject("select deleted_at is not null from p_stocks where id = ?",
                Boolean.class, stock.id())).isTrue();
        assertThat(jdbcTemplate.queryForObject("select deleted_by from p_stocks where id = ?",
                UUID.class, stock.id())).isEqualTo(actorId);
        assertThat(historyCount(orderId)).isEqualTo(2);
        // 재활성화되지 않으므로 일반 재고 조회에는 계속 나타나지 않는다.
        mockMvc.perform(get("/hubs/stocks/{stockId}", stock.id())).andExpect(status().isNotFound());
    }

    @Test
    void 삭제된_재고에_이미_처리된_차감_요청을_재전송하면_alreadyProcessed를_반환한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        markDeleted(stock.id(), UUID.randomUUID());

        decrease(orderId, stock, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));
    }

    // ---- 이력 조회 ----

    @Test
    void 이력이_없으면_빈_배열을_반환한다() throws Exception {
        UUID orderId = UUID.randomUUID();

        histories(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.histories").isEmpty());
    }

    @Test
    void 차감과_복원_이력을_처리_순서대로_반환한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        restore(orderId, stock).andExpect(status().isOk());

        histories(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.histories.length()").value(2))
                .andExpect(jsonPath("$.data.histories[0].type").value("DECREASE"))
                .andExpect(jsonPath("$.data.histories[0].stockId").value(stock.id().toString()))
                .andExpect(jsonPath("$.data.histories[0].quantity").value(10))
                .andExpect(jsonPath("$.data.histories[0].historyId").isNotEmpty())
                .andExpect(jsonPath("$.data.histories[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.histories[1].type").value("RESTORE"));
    }

    @Test
    void 처리_시각이_같으면_이력_ID_오름차순으로_정렬한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        // 일부러 ID가 큰 DECREASE를 먼저 넣어, 정렬이 삽입 순서가 아니라 id 기준임을 확인한다.
        jdbcTemplate.update("insert into p_stock_histories (id, order_id, stock_id, quantity, type, created_at) "
                + "values (?, ?, ?, 10, 'DECREASE', '2026-10-14 09:30:00')", high, orderId, stock.id());
        jdbcTemplate.update("insert into p_stock_histories (id, order_id, stock_id, quantity, type, created_at) "
                + "values (?, ?, ?, 10, 'RESTORE', '2026-10-14 09:30:00')", low, orderId, stock.id());

        histories(orderId)
                .andExpect(jsonPath("$.data.histories[0].historyId").value(low.toString()))
                .andExpect(jsonPath("$.data.histories[1].historyId").value(high.toString()));
    }

    @Test
    void 이력_조회의_orderId가_없거나_UUID가_아니면_400이다() throws Exception {
        mockMvc.perform(get("/internal/hubs/stocks/histories")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/internal/hubs/stocks/histories").param("orderId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ---- DB 제약 ----

    private void insertHistory(UUID orderId, UUID stockId, int quantity, String type) {
        jdbcTemplate.update("insert into p_stock_histories (id, order_id, stock_id, quantity, type, created_at) "
                + "values (?, ?, ?, ?, ?, now())", UUID.randomUUID(), orderId, stockId, quantity, type);
    }

    @Test
    void 이력_테이블은_양수가_아닌_수량과_알_수_없는_유형을_거부한다() {
        TestStock stock = createStock(100);

        assertThatThrownBy(() -> insertHistory(UUID.randomUUID(), stock.id(), 0, "DECREASE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(UUID.randomUUID(), stock.id(), 10, "INBOUND"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 이력_테이블은_없는_재고와_같은_주문_유형_중복을_거부한다() {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> insertHistory(UUID.randomUUID(), UUID.randomUUID(), 10, "DECREASE"))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertHistory(orderId, stock.id(), 10, "DECREASE");
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), 10, "DECREASE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 같은 주문의 다른 유형은 허용한다.
        insertHistory(orderId, stock.id(), 10, "RESTORE");
    }

    // ---- 동시성 ----

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

    private int statusOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getStatus();
    }

    @Test
    void 같은_주문을_같은_재고에_동시에_차감해도_한_번만_차감한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> statusOf(decrease(orderId, stock, 10)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(quantityOf(stock.id())).isEqualTo(90);
        assertThat(historyCount(orderId)).isEqualTo(1);
    }

    @Test
    void 같은_주문을_다른_재고에_동시에_차감하면_한_건만_성공하고_나머지는_409다() throws Exception {
        List<TestStock> stocks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            stocks.add(createStock(100));
        }
        UUID orderId = UUID.randomUUID();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (TestStock stock : stocks) {
            tasks.add(() -> statusOf(decrease(orderId, stock, 10)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        // 성공한 한 재고만 줄고 나머지는 롤백되어 그대로다.
        int reduced = stocks.stream().mapToInt(s -> 100 - quantityOf(s.id())).sum();
        assertThat(reduced).isEqualTo(10);
        assertThat(historyCount(orderId)).isEqualTo(1);
    }

    @Test
    void 서로_다른_주문이_동시에_차감해도_재고는_음수가_되지_않는다() throws Exception {
        TestStock stock = createStock(10);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID orderId = UUID.randomUUID();
            tasks.add(() -> statusOf(decrease(orderId, stock, 1)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(10);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(10);
        assertThat(quantityOf(stock.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_stock_histories where stock_id = ?", Integer.class, stock.id())).isEqualTo(10);
    }

    @Test
    void 같은_주문을_동시에_복원해도_한_번만_복원한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        decrease(orderId, stock, 10).andExpect(status().isOk());
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> statusOf(restore(orderId, stock)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(historyCount(orderId)).isEqualTo(2);
    }

    @Test
    void 차감과_복원이_동시에_들어와도_수량과_이력이_항상_일치한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        List<Callable<Integer>> tasks = List.of(
                () -> statusOf(decrease(orderId, stock, 10)),
                () -> statusOf(restore(orderId, stock)));

        List<Integer> statuses = runConcurrently(tasks);

        // 복원이 차감 이력을 찾기 전에 실행되면 409이며, 이 경우 호출자가 재시도하면 정상 복원된다.
        assertThat(statuses.get(0)).isEqualTo(200);
        assertThat(statuses.get(1)).isIn(200, 409);
        if (statuses.get(1) == 409) {
            restore(orderId, stock).andExpect(status().isOk());
        }
        // 어느 순서였든 DECREASE·RESTORE 이력이 한 건씩 있고 수량은 원래대로다.
        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(historyCount(orderId)).isEqualTo(2);
    }
}
