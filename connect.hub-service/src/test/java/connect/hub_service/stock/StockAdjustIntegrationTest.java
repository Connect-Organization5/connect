package connect.hub_service.stock;

import connect.hub_service.global.exception.ErrorCode;
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

// 주문 수량 수정에 따른 재고 조정(POST /internal/hubs/stocks/adjust)과 조정 이후의 복원 검증. 실제 PostgreSQL이 필요하다.
// quantityDelta = 수정 후 주문 수량 - 수정 전 주문 수량(양수: 재고 차감, 음수: 재고 증가).
@SpringBootTest
@AutoConfigureMockMvc
class StockAdjustIntegrationTest {

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

    private int historyCount(UUID orderId, String type) {
        return jdbcTemplate.queryForObject("select count(*) from p_stock_histories where order_id = ? and type = ?",
                Integer.class, orderId, type);
    }

    private int historyCount(UUID orderId) {
        return jdbcTemplate.queryForObject("select count(*) from p_stock_histories where order_id = ?",
                Integer.class, orderId);
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // 주문 생성 시 최초 차감을 만든다.
    private UUID decreased(TestStock stock, int quantity) throws Exception {
        UUID orderId = UUID.randomUUID();
        postJson("/internal/hubs/stocks/decrease", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s", "quantity": %d}
                """.formatted(orderId, stock.hubId(), stock.productId(), quantity)).andExpect(status().isOk());
        return orderId;
    }

    private static String adjustBody(UUID adjustmentId, UUID orderId, UUID hubId, UUID productId, Object delta) {
        return """
                {"adjustmentId": "%s", "orderId": "%s", "hubId": "%s", "productId": "%s", "quantityDelta": %s}
                """.formatted(adjustmentId, orderId, hubId, productId, delta);
    }

    private ResultActions adjust(UUID adjustmentId, UUID orderId, TestStock stock, Object delta) throws Exception {
        return postJson("/internal/hubs/stocks/adjust",
                adjustBody(adjustmentId, orderId, stock.hubId(), stock.productId(), delta));
    }

    private ResultActions adjust(UUID orderId, TestStock stock, int delta) throws Exception {
        return adjust(UUID.randomUUID(), orderId, stock, delta);
    }

    private ResultActions restore(UUID orderId, TestStock stock) throws Exception {
        return postJson("/internal/hubs/stocks/restore", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s"}
                """.formatted(orderId, stock.hubId(), stock.productId()));
    }

    private void markDeleted(UUID stockId, UUID userId) {
        jdbcTemplate.update("update p_stocks set deleted_at = now(), deleted_by = ? where id = ?", userId, stockId);
    }

    // ---- 조정 정상 흐름 ----

    @Test
    void 주문_수량을_3에서_1로_줄이면_재고_2가_늘고_점유_수량은_1이다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID adjustmentId = UUID.randomUUID();
        assertThat(quantityOf(stock.id())).isEqualTo(97);

        adjust(adjustmentId, orderId, stock, -2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjustmentId").value(adjustmentId.toString()))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.stockId").value(stock.id().toString()))
                .andExpect(jsonPath("$.data.processedDelta").value(-2))
                .andExpect(jsonPath("$.data.allocatedQuantity").value(1))
                .andExpect(jsonPath("$.data.currentStockQuantity").value(99))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(99);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(1);
    }

    @Test
    void 주문_수량을_1에서_5로_늘리면_재고_4가_줄고_점유_수량은_5다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 1);

        adjust(orderId, stock, 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedDelta").value(4))
                .andExpect(jsonPath("$.data.allocatedQuantity").value(5))
                .andExpect(jsonPath("$.data.currentStockQuantity").value(95))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(95);
    }

    @Test
    void 같은_delta라도_조정_ID가_다르면_각각_정상_처리한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 5);

        adjust(orderId, stock, -1).andExpect(status().isOk()).andExpect(jsonPath("$.data.allocatedQuantity").value(4));
        adjust(orderId, stock, -1).andExpect(status().isOk()).andExpect(jsonPath("$.data.allocatedQuantity").value(3));

        assertThat(quantityOf(stock.id())).isEqualTo(97);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(2);
    }

    @Test
    void 배송_수정_실패_보상처럼_새_조정_ID로_반대_delta를_보내면_재고가_원래대로_돌아온다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        adjust(orderId, stock, -2).andExpect(status().isOk());
        UUID compensationId = UUID.randomUUID();

        adjust(compensationId, orderId, stock, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocatedQuantity").value(3))
                .andExpect(jsonPath("$.data.currentStockQuantity").value(97));
        // 보상 요청을 다시 보내도 중복 반영되지 않는다.
        adjust(compensationId, orderId, stock, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));

        assertThat(quantityOf(stock.id())).isEqualTo(97);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(2);
    }

    // ---- 멱등성 ----

    @Test
    void 같은_조정_ID와_같은_요청을_다시_보내면_재변경_없이_alreadyProcessed를_반환한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID adjustmentId = UUID.randomUUID();
        adjust(adjustmentId, orderId, stock, -2).andExpect(status().isOk());

        adjust(adjustmentId, orderId, stock, -2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedDelta").value(-2))
                .andExpect(jsonPath("$.data.allocatedQuantity").value(1))
                .andExpect(jsonPath("$.data.currentStockQuantity").value(99))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));

        assertThat(quantityOf(stock.id())).isEqualTo(99);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(1);
    }

    @Test
    void 재호출_응답은_그_이후의_조정까지_반영한_현재_점유_수량을_돌려준다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID first = UUID.randomUUID();
        adjust(first, orderId, stock, -2).andExpect(status().isOk());
        adjust(orderId, stock, 4).andExpect(status().isOk());

        // processedDelta는 최초 값, allocatedQuantity·currentStockQuantity는 응답 시점의 현재 값이다.
        adjust(first, orderId, stock, -2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedDelta").value(-2))
                .andExpect(jsonPath("$.data.allocatedQuantity").value(5))
                .andExpect(jsonPath("$.data.currentStockQuantity").value(95))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));
    }

    @Test
    void 같은_조정_ID에_다른_delta_주문_허브_상품이면_409이고_재고는_바뀌지_않는다() throws Exception {
        TestStock stock = createStock(100);
        TestStock other = createStock(BUSAN, stock.productId(), 100);
        UUID orderId = decreased(stock, 5);
        UUID otherOrderId = decreased(createStock(50), 2);
        UUID adjustmentId = UUID.randomUUID();
        adjust(adjustmentId, orderId, stock, -2).andExpect(status().isOk());

        adjust(adjustmentId, orderId, stock, -1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_ADJUSTMENT_CONFLICT.getMessage()));
        adjust(adjustmentId, otherOrderId, stock, -2).andExpect(status().isConflict());
        adjust(adjustmentId, orderId, other, -2).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(97);
        assertThat(quantityOf(other.id())).isEqualTo(100);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(1);
    }

    // ---- 입력 검증 ----

    @Test
    void quantityDelta가_0이면_400이다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);

        adjust(UUID.randomUUID(), orderId, stock, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_QUANTITY_DELTA.getMessage()));

        assertThat(historyCount(orderId, "ADJUST")).isZero();
    }

    @Test
    void quantityDelta_필드가_없으면_400이다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);

        postJson("/internal/hubs/stocks/adjust", """
                {"adjustmentId": "%s", "orderId": "%s", "hubId": "%s", "productId": "%s"}
                """.formatted(UUID.randomUUID(), orderId, stock.hubId(), stock.productId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quantityDelta가_null이면_400이다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);

        adjust(UUID.randomUUID(), orderId, stock, "null").andExpect(status().isBadRequest());
        adjust(UUID.randomUUID(), orderId, stock, "\"abc\"").andExpect(status().isBadRequest());
    }

    @Test
    void 다른_필수_필드가_없으면_400이다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID adjustmentId = UUID.randomUUID();

        postJson("/internal/hubs/stocks/adjust", """
                {"orderId": "%s", "hubId": "%s", "productId": "%s", "quantityDelta": -1}
                """.formatted(orderId, stock.hubId(), stock.productId())).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/adjust", """
                {"adjustmentId": "%s", "hubId": "%s", "productId": "%s", "quantityDelta": -1}
                """.formatted(adjustmentId, stock.hubId(), stock.productId())).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/adjust", """
                {"adjustmentId": "%s", "orderId": "%s", "productId": "%s", "quantityDelta": -1}
                """.formatted(adjustmentId, orderId, stock.productId())).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/adjust", """
                {"adjustmentId": "%s", "orderId": "%s", "hubId": "%s", "quantityDelta": -1}
                """.formatted(adjustmentId, orderId, stock.hubId())).andExpect(status().isBadRequest());
        postJson("/internal/hubs/stocks/adjust", """
                {"adjustmentId": "not-a-uuid", "orderId": "%s", "hubId": "%s", "productId": "%s", "quantityDelta": -1}
                """.formatted(orderId, stock.hubId(), stock.productId())).andExpect(status().isBadRequest());

        assertThat(historyCount(orderId, "ADJUST")).isZero();
    }

    @Test
    void int_범위_경계의_delta는_오버플로_없이_409로_거절한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);

        // 3 + MAX_VALUE는 int 범위를 넘는다.
        adjust(UUID.randomUUID(), orderId, stock, Integer.MAX_VALUE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_QUANTITY_OVERFLOW.getMessage()));
        // MIN_VALUE의 절댓값은 int로 표현할 수 없지만 long으로 계산해 부호가 뒤집히지 않고 점유 1 미만으로 거절한다.
        adjust(UUID.randomUUID(), orderId, stock, Integer.MIN_VALUE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_ALLOCATION_UNDERFLOW.getMessage()));

        assertThat(quantityOf(stock.id())).isEqualTo(97);
        assertThat(historyCount(orderId, "ADJUST")).isZero();
    }

    // ---- 비즈니스 검증 ----

    @Test
    void 추가_차감할_재고가_부족하면_409이고_수량과_이력은_바뀌지_않는다() throws Exception {
        TestStock stock = createStock(5);
        UUID orderId = decreased(stock, 3);

        adjust(orderId, stock, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.INSUFFICIENT_STOCK.getMessage()));

        assertThat(quantityOf(stock.id())).isEqualTo(2);
        assertThat(historyCount(orderId, "ADJUST")).isZero();
    }

    @Test
    void 재고가_정확히_모자라지_않으면_0까지_추가_차감할_수_있다() throws Exception {
        TestStock stock = createStock(5);
        UUID orderId = decreased(stock, 3);

        adjust(orderId, stock, 2).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentStockQuantity").value(0));
    }

    @Test
    void 점유_수량보다_큰_음수_delta는_점유_1_미만이라_409다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);

        // 3 - 3 = 0이므로 조정이 아니라 restore를 써야 한다.
        adjust(orderId, stock, -3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_ALLOCATION_UNDERFLOW.getMessage()));
        adjust(orderId, stock, -10).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(97);
        assertThat(historyCount(orderId, "ADJUST")).isZero();
    }

    @Test
    void 최초_차감_이력이_없는_주문이면_409다() throws Exception {
        TestStock stock = createStock(100);

        adjust(UUID.randomUUID(), stock, -1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.DECREASE_HISTORY_NOT_FOUND.getMessage()));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 최초_차감_재고와_다른_허브나_상품이면_409다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);

        postJson("/internal/hubs/stocks/adjust", adjustBody(UUID.randomUUID(), orderId, stock.hubId(), UUID.randomUUID(), -1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_HISTORY_MISMATCH.getMessage()));
        postJson("/internal/hubs/stocks/adjust", adjustBody(UUID.randomUUID(), orderId, BUSAN, stock.productId(), -1))
                .andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(97);
    }

    @Test
    void 복원이_끝난_주문은_조정할_수_없다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        restore(orderId, stock).andExpect(status().isOk());

        adjust(orderId, stock, -1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_ALREADY_RESTORED.getMessage()));
        adjust(orderId, stock, 1).andExpect(status().isConflict());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(historyCount(orderId, "ADJUST")).isZero();
    }

    @Test
    void 다른_주문이_이미_쓴_조정_ID를_재사용하면_409다() throws Exception {
        TestStock first = createStock(100);
        TestStock second = createStock(100);
        UUID firstOrder = decreased(first, 3);
        UUID secondOrder = decreased(second, 3);
        UUID adjustmentId = UUID.randomUUID();
        adjust(adjustmentId, firstOrder, first, -1).andExpect(status().isOk());

        adjust(adjustmentId, secondOrder, second, -1).andExpect(status().isConflict());

        assertThat(quantityOf(second.id())).isEqualTo(97);
    }

    // ---- 논리 삭제된 재고 ----

    @Test
    void 삭제된_재고에_양수_delta는_409이고_삭제_상태는_유지된다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID userId = UUID.randomUUID();
        markDeleted(stock.id(), userId);

        adjust(orderId, stock, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_DELETED.getMessage()));

        assertThat(quantityOf(stock.id())).isEqualTo(97);
        assertThat(historyCount(orderId, "ADJUST")).isZero();
    }

    @Test
    void 삭제된_재고에도_음수_delta는_허용하고_삭제_상태는_유지된다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID userId = UUID.randomUUID();
        markDeleted(stock.id(), userId);

        adjust(orderId, stock, -2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStockQuantity").value(99));

        assertThat(jdbcTemplate.queryForObject("select deleted_at is not null from p_stocks where id = ?",
                Boolean.class, stock.id())).isTrue();
        assertThat(jdbcTemplate.queryForObject("select deleted_by from p_stocks where id = ?",
                UUID.class, stock.id())).isEqualTo(userId);
        mockMvc.perform(get("/hubs/stocks/{stockId}", stock.id())).andExpect(status().isNotFound());
    }

    // ---- 조정 이후 복원 ----

    @Test
    void 조정이_없는_주문은_최초_차감_수량_전체를_복원한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredQuantity").value(3))
                .andExpect(jsonPath("$.data.currentQuantity").value(100));
    }

    @Test
    void 수량을_3에서_1로_조정한_뒤_취소하면_1개만_복원한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        adjust(orderId, stock, -2).andExpect(status().isOk());

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredQuantity").value(1))
                .andExpect(jsonPath("$.data.currentQuantity").value(100))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(jdbcTemplate.queryForObject(
                "select quantity from p_stock_histories where order_id = ? and type = 'RESTORE'",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void 수량을_3에서_1로_다시_5로_조정한_뒤_취소하면_5개를_복원한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        adjust(orderId, stock, -2).andExpect(status().isOk());
        adjust(orderId, stock, 4).andExpect(status().isOk());
        assertThat(quantityOf(stock.id())).isEqualTo(95);

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredQuantity").value(5))
                .andExpect(jsonPath("$.data.currentQuantity").value(100));
    }

    @Test
    void 조정_후_복원을_다시_보내면_재복원하지_않고_alreadyProcessed를_반환한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        adjust(orderId, stock, -2).andExpect(status().isOk());
        restore(orderId, stock).andExpect(status().isOk());

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredQuantity").value(1))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(historyCount(orderId, "RESTORE")).isEqualTo(1);
    }

    @Test
    void 삭제된_재고도_조정된_수량만큼_복원하고_삭제_상태는_유지된다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        adjust(orderId, stock, -2).andExpect(status().isOk());
        UUID userId = UUID.randomUUID();
        markDeleted(stock.id(), userId);

        restore(orderId, stock)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredQuantity").value(1))
                .andExpect(jsonPath("$.data.currentQuantity").value(100));

        assertThat(jdbcTemplate.queryForObject("select deleted_by from p_stocks where id = ?",
                UUID.class, stock.id())).isEqualTo(userId);
    }

    // ---- 이력 조회 ----

    @Test
    void 이력_조회는_조정_이력과_증감량_조정_ID를_함께_반환한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID adjustmentId = UUID.randomUUID();
        adjust(adjustmentId, orderId, stock, -2).andExpect(status().isOk());
        restore(orderId, stock).andExpect(status().isOk());

        mockMvc.perform(get("/internal/hubs/stocks/histories").param("orderId", orderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.histories.length()").value(3))
                .andExpect(jsonPath("$.data.histories[0].type").value("DECREASE"))
                .andExpect(jsonPath("$.data.histories[0].quantity").value(3))
                .andExpect(jsonPath("$.data.histories[0].quantityDelta").doesNotExist())
                .andExpect(jsonPath("$.data.histories[0].adjustmentId").doesNotExist())
                .andExpect(jsonPath("$.data.histories[1].type").value("ADJUST"))
                .andExpect(jsonPath("$.data.histories[1].quantity").doesNotExist())
                .andExpect(jsonPath("$.data.histories[1].quantityDelta").value(-2))
                .andExpect(jsonPath("$.data.histories[1].adjustmentId").value(adjustmentId.toString()))
                .andExpect(jsonPath("$.data.histories[2].type").value("RESTORE"))
                .andExpect(jsonPath("$.data.histories[2].quantity").value(1));
    }

    // ---- DB 제약 ----

    // idempotencyKey는 DECREASE·RESTORE면 orderId, ADJUST면 adjustmentId여야 한다.
    private void insertHistory(UUID orderId, UUID stockId, String type, UUID idempotencyKey, Integer quantity, Integer delta) {
        jdbcTemplate.update("insert into p_stock_histories (id, order_id, stock_id, type, idempotency_key, quantity, quantity_delta, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), orderId, stockId, type, idempotencyKey, quantity, delta);
    }

    @Test
    void 이력_테이블은_유형별로_허용되지_않는_필드_조합을_거부한다() {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();

        // ADJUST는 0이 아닌 증감량이 필수이고 quantity는 null이다.
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "ADJUST", UUID.randomUUID(), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "ADJUST", UUID.randomUUID(), null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "ADJUST", UUID.randomUUID(), 2, -2))
                .isInstanceOf(DataIntegrityViolationException.class);
        // DECREASE·RESTORE는 양수 quantity만 가지며 증감량은 null이다.
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "DECREASE", orderId, 3, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "RESTORE", orderId, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "RESTORE", orderId, 0, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 멱등 키는 모든 유형에서 필수이고, 알 수 없는 유형은 거부한다.
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "ADJUST", null, null, -2))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "INBOUND", orderId, 3, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(historyCount(orderId)).isZero();
    }

    @Test
    void 이력_테이블은_DECREASE와_RESTORE의_멱등_키가_주문_ID와_다르면_거부한다() {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "DECREASE", UUID.randomUUID(), 3, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "RESTORE", UUID.randomUUID(), 3, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(historyCount(orderId)).isZero();
    }

    @Test
    void 이력_테이블은_한_주문에_여러_ADJUST를_허용하지만_같은_유형과_멱등_키의_중복은_거부한다() {
        TestStock stock = createStock(100);
        UUID orderId = UUID.randomUUID();
        UUID adjustmentId = UUID.randomUUID();

        insertHistory(orderId, stock.id(), "DECREASE", orderId, 3, null);
        insertHistory(orderId, stock.id(), "ADJUST", adjustmentId, null, -1);
        insertHistory(orderId, stock.id(), "ADJUST", UUID.randomUUID(), null, -1);
        insertHistory(orderId, stock.id(), "RESTORE", orderId, 1, null);

        // (DECREASE, orderId), (RESTORE, orderId), (ADJUST, adjustmentId)는 각각 한 번만 저장된다.
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "DECREASE", orderId, 3, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(orderId, stock.id(), "RESTORE", orderId, 1, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHistory(UUID.randomUUID(), stock.id(), "ADJUST", adjustmentId, null, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(2);
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
    void 같은_조정_요청이_동시에_들어와도_한_번만_반영한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        UUID adjustmentId = UUID.randomUUID();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> statusOf(adjust(adjustmentId, orderId, stock, -2)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(quantityOf(stock.id())).isEqualTo(99);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(1);
    }

    @Test
    void 서로_다른_주문이_같은_조정_ID로_동시에_들어오면_한_건만_반영한다() throws Exception {
        List<TestStock> stocks = new ArrayList<>();
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TestStock stock = createStock(100);
            stocks.add(stock);
            orders.add(decreased(stock, 3));
        }
        UUID adjustmentId = UUID.randomUUID();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < stocks.size(); i++) {
            TestStock stock = stocks.get(i);
            UUID orderId = orders.get(i);
            tasks.add(() -> statusOf(adjust(adjustmentId, orderId, stock, -2)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        // 성공한 한 재고만 2 늘고 나머지는 롤백되어 그대로다.
        int restored = stocks.stream().mapToInt(s -> quantityOf(s.id()) - 97).sum();
        assertThat(restored).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_stock_histories where type = 'ADJUST' and idempotency_key = ?",
                Integer.class, adjustmentId)).isEqualTo(1);
    }

    @Test
    void 서로_다른_조정이_동시에_들어와도_증감량이_유실되지_않는다() throws Exception {
        TestStock stock = createStock(1000);
        UUID orderId = decreased(stock, 20);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> statusOf(adjust(orderId, stock, -1)));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).allMatch(s -> s == 200);
        // 20 - 8 = 12개를 점유하고, 재고는 980 + 8 = 988이다.
        assertThat(quantityOf(stock.id())).isEqualTo(988);
        assertThat(historyCount(orderId, "ADJUST")).isEqualTo(8);
    }

    @Test
    void 조정과_복원이_동시에_들어와도_최종_재고와_이력이_일치한다() throws Exception {
        TestStock stock = createStock(100);
        UUID orderId = decreased(stock, 3);
        List<Callable<Integer>> tasks = List.of(
                () -> statusOf(adjust(orderId, stock, -2)),
                () -> statusOf(restore(orderId, stock)));

        List<Integer> statuses = runConcurrently(tasks);

        // 복원이 먼저면 조정은 복원 완료로 409, 조정이 먼저면 둘 다 200이다. 어느 쪽이든 재고는 원래대로 100이다.
        assertThat(statuses.get(0)).isIn(200, 409);
        assertThat(statuses.get(1)).isEqualTo(200);
        assertThat(quantityOf(stock.id())).isEqualTo(100);
        assertThat(historyCount(orderId, "RESTORE")).isEqualTo(1);
        long adjusts = historyCount(orderId, "ADJUST");
        assertThat(adjusts).isEqualTo(statuses.get(0) == 200 ? 1 : 0);
    }
}
