package connect.hub_service.stock;

import connect.hub_service.client.company.CompanyClient;
import connect.hub_service.client.company.dto.CompanyInfo;
import connect.hub_service.client.company.dto.ProductInfo;
import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.hub.seed.HubSeedData;
import connect.hub_service.hub.seed.HubSeedLoader;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
    // Company 응답은 Mock이다. 서울 업체는 서울 허브, 부산 업체는 부산 허브 소속이며 각각 관리자가 있다.
    private static final UUID SEOUL_COMPANY = UUID.randomUUID();
    private static final UUID BUSAN_COMPANY = UUID.randomUUID();
    private static final UUID SEOUL_MANAGER = UUID.randomUUID();
    private static final UUID BUSAN_MANAGER = UUID.randomUUID();
    // MASTER 요청자, 어느 업체의 관리자도 아닌 USER 요청자
    private static final UUID MASTER_USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();

    private record TestStock(UUID id, UUID hubId, UUID productId) {
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    HubSeedLoader hubSeedLoader;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @MockitoBean
    CompanyClient companyClient;

    private final List<UUID> createdProducts = new ArrayList<>();

    @BeforeEach
    void seedHubs() {
        hubSeedLoader.load();
        given(companyClient.getCompany(SEOUL_COMPANY))
                .willReturn(ApiResponse.ok("업체가 조회되었습니다.", new CompanyInfo(SEOUL_COMPANY, SEOUL_MANAGER, SEOUL)));
        given(companyClient.getCompany(BUSAN_COMPANY))
                .willReturn(ApiResponse.ok("업체가 조회되었습니다.", new CompanyInfo(BUSAN_COMPANY, BUSAN_MANAGER, BUSAN)));
        given(companyClient.getProduct(any(), any()))
                .willAnswer(invocation -> ApiResponse.ok("상품이 조회되었습니다.", new ProductInfo(invocation.getArgument(1))));
    }

    @AfterEach
    void cleanUp() {
        for (UUID productId : createdProducts) {
            jdbcTemplate.update(
                    "delete from p_stock_inbounds where stock_id in (select id from p_stocks where product_id = ?)", productId);
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

    // 요청자는 역할에 맞춰 고른다(MASTER면 MASTER 사용자, 그 외는 어느 업체의 관리자도 아닌 사용자).
    private ResultActions inbound(String role, String body) throws Exception {
        return inboundAs("MASTER".equals(role) ? MASTER_USER : OTHER_USER, role, body);
    }

    private ResultActions inboundAs(UUID userId, String role, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/hubs/stocks/inbound")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    // 업체는 허브에 맞춰 고른다(부산 허브면 부산 업체, 그 외는 서울 업체).
    private static String body(Object hubId, Object productId, String quantityJson) {
        return body(BUSAN.equals(hubId) ? BUSAN_COMPANY : SEOUL_COMPANY, hubId, productId, quantityJson);
    }

    // 입고 ID는 요청마다 새로 만든다(새 입고). 같은 입고의 재시도는 bodyWithInboundId로 같은 ID를 보낸다.
    private static String body(Object companyId, Object hubId, Object productId, String quantityJson) {
        return bodyWithInboundId(UUID.randomUUID(), companyId, hubId, productId, quantityJson);
    }

    private static String bodyWithInboundId(Object inboundId, Object companyId, Object hubId, Object productId,
                                            String quantityJson) {
        return "{\"inboundId\":" + json(inboundId) + ",\"companyId\":" + json(companyId) + ",\"hubId\":" + json(hubId)
                + ",\"productId\":" + json(productId) + ",\"quantity\":" + quantityJson + "}";
    }

    private int inboundCount(UUID stockId) {
        return jdbcTemplate.queryForObject("select count(*) from p_stock_inbounds where stock_id = ?", Integer.class, stockId);
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
                .andExpect(jsonPath("$.data.inboundQuantity").value(50))
                .andExpect(jsonPath("$.data.currentQuantity").value(150))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(150);
        // 조회 API에서도 합산된 수량이 보인다
        mockMvc.perform(get("/hubs/stocks/{id}", stock.id()))
                .andExpect(jsonPath("$.data.quantity").value(150));
    }

    @Test
    void 수량_0인_재고에도_입고할_수_있다() throws Exception {
        TestStock stock = createStock(0);

        inbound("MASTER", stock, "7").andExpect(status().isOk()).andExpect(jsonPath("$.data.currentQuantity").value(7));
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
    void 입고_ID가_다르면_같은_수량이라도_각각_더한다() throws Exception {
        TestStock stock = createStock(100);

        inbound("MASTER", stock, "50").andExpect(status().isOk());
        inbound("MASTER", stock, "50").andExpect(status().isOk());

        assertThat(quantityOf(stock.id())).isEqualTo(200);
        assertThat(inboundCount(stock.id())).isEqualTo(2);
    }

    // ---------- 멱등성(inboundId) ----------

    @Test
    void 같은_입고_ID로_다시_보내면_수량을_다시_더하지_않는다() throws Exception {
        TestStock stock = createStock(100);
        UUID inboundId = UUID.randomUUID();
        String body = bodyWithInboundId(inboundId, SEOUL_COMPANY, SEOUL, stock.productId(), "50");

        inbound("MASTER", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));
        inbound("MASTER", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inboundId").value(inboundId.toString()))
                .andExpect(jsonPath("$.data.inboundQuantity").value(50))
                .andExpect(jsonPath("$.data.currentQuantity").value(150))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(true));

        assertThat(quantityOf(stock.id())).isEqualTo(150);
        assertThat(inboundCount(stock.id())).isEqualTo(1);
    }

    @Test
    void 같은_입고_ID에_다른_수량이나_다른_재고면_409이다() throws Exception {
        TestStock stock = createStock(100);
        TestStock other = createStock(100);
        UUID inboundId = UUID.randomUUID();
        inbound("MASTER", bodyWithInboundId(inboundId, SEOUL_COMPANY, SEOUL, stock.productId(), "50"))
                .andExpect(status().isOk());

        inbound("MASTER", bodyWithInboundId(inboundId, SEOUL_COMPANY, SEOUL, stock.productId(), "30"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INBOUND_CONFLICT"));
        inbound("MASTER", bodyWithInboundId(inboundId, SEOUL_COMPANY, SEOUL, other.productId(), "50"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_INBOUND_CONFLICT"));

        assertThat(quantityOf(stock.id())).isEqualTo(150);
        assertThat(quantityOf(other.id())).isEqualTo(100);
    }

    @Test
    void 입고_기록에_요청자와_수량이_남고_실패한_입고는_기록되지_않는다() throws Exception {
        TestStock stock = createStock(100);

        inboundAs(SEOUL_MANAGER, "USER", body(SEOUL_COMPANY, SEOUL, stock.productId(), "30")).andExpect(status().isOk());
        inbound("USER", stock, "10").andExpect(status().isForbidden());          // 업체 관리자 아님
        inbound("MASTER", stock, "0").andExpect(status().isBadRequest());         // 검증 실패

        assertThat(jdbcTemplate.queryForObject(
                "select inbound_by::text || '|' || quantity from p_stock_inbounds where stock_id = ?", String.class, stock.id()))
                .isEqualTo(SEOUL_MANAGER + "|30");
        assertThat(inboundCount(stock.id())).isEqualTo(1);
    }

    @Test
    void 입고_ID가_없으면_400이다() throws Exception {
        TestStock stock = createStock(100);

        inbound("MASTER", "{\"companyId\":\"" + SEOUL_COMPANY + "\",\"hubId\":\"" + SEOUL + "\",\"productId\":\""
                + stock.productId() + "\",\"quantity\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 같은_입고_ID로_동시에_보내도_한_번만_반영된다() throws Exception {
        TestStock stock = createStock(0);
        String body = bodyWithInboundId(UUID.randomUUID(), SEOUL_COMPANY, SEOUL, stock.productId(), "5");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return inbound("MASTER", body).andReturn().getResponse().getStatus();
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

        assertThat(quantityOf(stock.id())).isEqualTo(5);
        assertThat(inboundCount(stock.id())).isEqualTo(1);
    }

    // ---------- 입고 이력 조회 ----------

    @Test
    void 입고_이력을_최신순으로_조회한다() throws Exception {
        TestStock stock = createStock(0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        inbound("MASTER", bodyWithInboundId(first, SEOUL_COMPANY, SEOUL, stock.productId(), "3")).andExpect(status().isOk());
        inbound("MASTER", bodyWithInboundId(second, SEOUL_COMPANY, SEOUL, stock.productId(), "4")).andExpect(status().isOk());

        mockMvc.perform(get("/hubs/stocks/{id}/inbounds", stock.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].inboundId").value(second.toString()))
                .andExpect(jsonPath("$.data.content[0].quantity").value(4))
                .andExpect(jsonPath("$.data.content[0].inboundBy").value(MASTER_USER.toString()))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                .andExpect(jsonPath("$.data.content[1].inboundId").value(first.toString()));

        mockMvc.perform(get("/hubs/stocks/{id}/inbounds", stock.id()).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.data.content[0].inboundId").value(first.toString()))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void 입고_이력은_삭제된_재고도_조회하고_없는_재고는_404다() throws Exception {
        TestStock stock = createStock(0);
        inbound("MASTER", stock, "3").andExpect(status().isOk());
        jdbcTemplate.update("update p_stocks set deleted_at = now() where id = ?", stock.id());

        mockMvc.perform(get("/hubs/stocks/{id}/inbounds", stock.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/hubs/stocks/{id}/inbounds", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void 업체_관리자가_아닌_USER는_403이고_수량은_그대로다() throws Exception {
        TestStock stock = createStock(100);

        inbound("USER", stock, "50").andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 업체_관리자는_자기_업체_상품의_재고에_입고한다() throws Exception {
        TestStock stock = createStock(100);

        inboundAs(SEOUL_MANAGER, "USER", body(SEOUL_COMPANY, SEOUL, stock.productId(), "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentQuantity").value(130));
    }

    @Test
    void 다른_업체의_관리자는_403이고_수량은_그대로다() throws Exception {
        TestStock stock = createStock(100);

        inboundAs(BUSAN_MANAGER, "USER", body(SEOUL_COMPANY, SEOUL, stock.productId(), "30"))
                .andExpect(status().isForbidden());

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 업체의_소속_허브가_아닌_재고는_409이다() throws Exception {
        TestStock busan = createStock(BUSAN, UUID.randomUUID(), 10);

        inbound("MASTER", body(SEOUL_COMPANY, BUSAN, busan.productId(), "5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("요청한 허브가 업체의 소속 허브와 일치하지 않습니다."));

        assertThat(quantityOf(busan.id())).isEqualTo(10);
    }

    @Test
    void 없는_업체는_404이다() throws Exception {
        TestStock stock = createStock(100);
        UUID company = UUID.randomUUID();
        given(companyClient.getCompany(company)).willThrow(notFound());

        inbound("MASTER", body(company, SEOUL, stock.productId(), "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("업체를 찾을 수 없습니다."));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    @Test
    void 업체_ID나_사용자_ID_헤더가_없으면_400이다() throws Exception {
        TestStock stock = createStock(100);

        inbound("MASTER", body(null, SEOUL, stock.productId(), "5")).andExpect(status().isBadRequest());
        inboundAs(null, "MASTER", body(SEOUL, stock.productId(), "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다. (X-User-Id)"));

        assertThat(quantityOf(stock.id())).isEqualTo(100);
    }

    private static FeignException notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/companies", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(404).reason("Not Found").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("CompanyClient", response);
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
