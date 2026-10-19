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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 PostgreSQL에 연결해 실행한다. POSTGRES_USER/POSTGRES_PASSWORD 환경변수가 필요하다.
// 테스트가 만든 재고는 상품 ID로 추적해 종료 시 삭제한다.
@SpringBootTest
@AutoConfigureMockMvc
class StockApiIntegrationTest {

    private static final UUID SEOUL = HubSeedData.hubs().get(0).id();
    private static final UUID BUSAN = HubSeedData.hubs().get(3).id();
    // Company 응답은 Mock이다. 서울 업체는 서울 허브, 부산 업체는 부산 허브 소속이다.
    private static final UUID SEOUL_COMPANY = UUID.randomUUID();
    private static final UUID BUSAN_COMPANY = UUID.randomUUID();

    @Autowired
    MockMvc mockMvc;
    @Autowired
    HubSeedLoader hubSeedLoader;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    JsonMapper jsonMapper;
    @MockitoBean
    CompanyClient companyClient;

    private final List<UUID> createdProducts = new ArrayList<>();

    @BeforeEach
    void seedHubs() {
        hubSeedLoader.load();
        given(companyClient.getCompany(SEOUL_COMPANY))
                .willReturn(ApiResponse.ok("업체가 조회되었습니다.", new CompanyInfo(SEOUL_COMPANY, SEOUL)));
        given(companyClient.getCompany(BUSAN_COMPANY))
                .willReturn(ApiResponse.ok("업체가 조회되었습니다.", new CompanyInfo(BUSAN_COMPANY, BUSAN)));
        given(companyClient.getProduct(any(), any()))
                .willAnswer(invocation -> ApiResponse.ok("상품이 조회되었습니다.", new ProductInfo(invocation.getArgument(1))));
    }

    private static FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/companies", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).reason("error").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("CompanyClient", response);
    }

    @AfterEach
    void cleanUp() {
        for (UUID productId : createdProducts) {
            jdbcTemplate.update("delete from p_stocks where product_id = ?", productId);
        }
        createdProducts.clear();
    }

    private UUID newProduct() {
        UUID id = UUID.randomUUID();
        createdProducts.add(id);
        return id;
    }

    // 업체는 허브에 맞춰 고른다(부산 허브면 부산 업체, 그 외는 서울 업체).
    private ResultActions register(String role, UUID hubId, UUID productId, Object quantity) throws Exception {
        return register(role, BUSAN.equals(hubId) ? BUSAN_COMPANY : SEOUL_COMPANY, hubId, productId, quantity);
    }

    private ResultActions register(String role, UUID companyId, UUID hubId, UUID productId, Object quantity) throws Exception {
        String body = "{\"companyId\":" + (companyId == null ? "null" : "\"" + companyId + "\"")
                + ",\"hubId\":" + (hubId == null ? "null" : "\"" + hubId + "\"")
                + ",\"productId\":" + (productId == null ? "null" : "\"" + productId + "\"")
                + ",\"quantity\":" + quantity + "}";
        var request = post("/hubs/stocks").contentType(MediaType.APPLICATION_JSON).content(body);
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    private String registerAndGetId(UUID hubId, UUID productId, int quantity) throws Exception {
        String json = register("MASTER", hubId, productId, quantity)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"stockId\":\"([^\"]+)\".*", "$1");
    }

    // ---------- 등록 ----------

    @Test
    void MASTER는_재고를_등록한다() throws Exception {
        UUID product = newProduct();

        register("MASTER", SEOUL, product, 100)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockId").exists())
                .andExpect(jsonPath("$.data.hubId").value(SEOUL.toString()))
                .andExpect(jsonPath("$.data.productId").value(product.toString()))
                .andExpect(jsonPath("$.data.quantity").value(100));
    }

    @Test
    void 수량_0도_등록할_수_있다() throws Exception {
        register("MASTER", SEOUL, newProduct(), 0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantity").value(0));
    }

    @Test
    void MASTER가_아니면_403이다() throws Exception {
        UUID product = newProduct();

        register("USER", SEOUL, product, 10)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from p_stocks where product_id = ?", Integer.class, product);
        assertThat(count).isZero();
    }

    @Test
    void 역할_헤더가_없으면_400이다() throws Exception {
        register(null, SEOUL, newProduct(), 10)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 음수_수량과_필수값_누락은_400이다() throws Exception {
        register("MASTER", SEOUL, newProduct(), -1).andExpect(status().isBadRequest());
        register("MASTER", SEOUL, newProduct(), "null").andExpect(status().isBadRequest());
        register("MASTER", null, newProduct(), 1).andExpect(status().isBadRequest());
        register("MASTER", SEOUL, null, 1).andExpect(status().isBadRequest());
        register("MASTER", null, SEOUL, newProduct(), 1).andExpect(status().isBadRequest());
        mockMvc.perform(post("/hubs/stocks").header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hubId\":\"abc\",\"productId\":\"def\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_허브는_404이다() throws Exception {
        register("MASTER", UUID.randomUUID(), newProduct(), 10)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("허브를 찾을 수 없습니다."));
    }

    @Test
    void 같은_허브_상품은_409이고_수량을_덮어쓰거나_합산하지_않는다() throws Exception {
        UUID product = newProduct();
        register("MASTER", SEOUL, product, 100).andExpect(status().isCreated());

        register("MASTER", SEOUL, product, 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        Integer quantity = jdbcTemplate.queryForObject(
                "select quantity from p_stocks where hub_id = ? and product_id = ?", Integer.class, SEOUL, product);
        assertThat(quantity).isEqualTo(100);
    }

    @Test
    void 같은_상품도_다른_허브에는_등록할_수_있다() throws Exception {
        UUID product = newProduct();
        register("MASTER", SEOUL, product, 1).andExpect(status().isCreated());
        register("MASTER", BUSAN, product, 2).andExpect(status().isCreated());
    }

    @Test
    void 논리_삭제된_재고와_같은_허브_상품도_재등록은_409이다() throws Exception {
        UUID product = newProduct();
        register("MASTER", SEOUL, product, 10).andExpect(status().isCreated());
        jdbcTemplate.update("update p_stocks set deleted_at = now() where product_id = ?", product);

        register("MASTER", SEOUL, product, 10).andExpect(status().isConflict());
    }

    // ---------- Company 검증 ----------

    @Test
    void 없는_업체는_404이고_재고를_만들지_않는다() throws Exception {
        UUID company = UUID.randomUUID();
        UUID product = newProduct();
        given(companyClient.getCompany(company)).willThrow(feignError(404));

        register("MASTER", company, SEOUL, product, 10)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("업체를 찾을 수 없습니다."));
        assertNoStock(product);
    }

    @Test
    void 업체에_없는_상품은_404이다() throws Exception {
        UUID product = newProduct();
        given(companyClient.getProduct(SEOUL_COMPANY, product)).willThrow(feignError(404));

        register("MASTER", SEOUL_COMPANY, SEOUL, product, 10)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("업체의 상품을 찾을 수 없습니다."));
        assertNoStock(product);
    }

    @Test
    void 업체의_소속_허브가_아니면_409이다() throws Exception {
        UUID product = newProduct();

        register("MASTER", SEOUL_COMPANY, BUSAN, product, 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("요청한 허브가 업체의 소속 허브와 일치하지 않습니다."));
        assertNoStock(product);
    }

    @Test
    void Company_장애면_503이다() throws Exception {
        UUID company = UUID.randomUUID();
        UUID product = newProduct();
        given(companyClient.getCompany(company)).willThrow(feignError(500));

        register("MASTER", company, SEOUL, product, 10)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("업체 서비스 호출에 실패했습니다."));

        given(companyClient.getProduct(SEOUL_COMPANY, product)).willThrow(feignError(503));
        register("MASTER", SEOUL_COMPANY, SEOUL, product, 10)
                .andExpect(status().isServiceUnavailable());
        assertNoStock(product);
    }

    @Test
    void 없는_허브는_Company를_호출하지_않는다() throws Exception {
        register("MASTER", SEOUL_COMPANY, UUID.randomUUID(), newProduct(), 10)
                .andExpect(status().isNotFound());

        then(companyClient).shouldHaveNoInteractions();
    }

    @Test
    void Company_응답_JSON을_CompanyInfo로_읽는다() {
        // Company CompanyResponse의 실제 필드(id, userId, hubId, name, address, createdAt). 필요 없는 필드는 무시한다.
        String json = """
                {"success":true,"message":"업체가 조회되었습니다.","data":{
                  "id":"%s","userId":null,"hubId":"%s","name":"업체","address":"주소","createdAt":"2026-10-19T10:00:00"
                },"timestamp":"2026-10-19T10:00:00"}
                """.formatted(SEOUL_COMPANY, SEOUL);

        ApiResponse<CompanyInfo> response = jsonMapper.readValue(json, new TypeReference<ApiResponse<CompanyInfo>>() {
        });

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(new CompanyInfo(SEOUL_COMPANY, SEOUL));
    }

    private void assertNoStock(UUID productId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from p_stocks where product_id = ?", Integer.class, productId);
        assertThat(count).isZero();
    }

    // ---------- DB 제약 ----------

    @Test
    void UNIQUE_CHECK_FK_제약이_DB에_있다() {
        List<String> defs = jdbcTemplate.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint where conrelid = 'p_stocks'::regclass",
                String.class);

        assertThat(defs).anyMatch(d -> d.startsWith("UNIQUE") && d.contains("hub_id") && d.contains("product_id"));
        assertThat(defs).anyMatch(d -> d.startsWith("CHECK") && d.contains("quantity >= 0"));
        assertThat(defs).anyMatch(d -> d.startsWith("FOREIGN KEY (hub_id)") && d.contains("p_hubs"));
    }

    @Test
    void 음수_수량은_DB_제약에서도_거부된다() {
        UUID product = newProduct();
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbcTemplate.update(
                "insert into p_stocks (id, hub_id, product_id, quantity) values (?, ?, ?, -1)",
                UUID.randomUUID(), SEOUL, product));
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_페이지_정보와_함께_반환한다() throws Exception {
        UUID product = newProduct();
        registerAndGetId(SEOUL, product, 7);

        mockMvc.perform(get("/hubs/stocks").param("productId", product.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].quantity").value(7))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void 허브_상품_조건은_AND로_필터링한다() throws Exception {
        UUID product = newProduct();
        registerAndGetId(SEOUL, product, 1);
        registerAndGetId(BUSAN, product, 2);

        mockMvc.perform(get("/hubs/stocks").param("productId", product.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/hubs/stocks")
                        .param("hubId", BUSAN.toString()).param("productId", product.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].hubId").value(BUSAN.toString()));
    }

    @Test
    void 논리_삭제된_재고는_목록에서_제외한다() throws Exception {
        UUID product = newProduct();
        registerAndGetId(SEOUL, product, 1);
        jdbcTemplate.update("update p_stocks set deleted_at = now() where product_id = ?", product);

        mockMvc.perform(get("/hubs/stocks").param("productId", product.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 존재하지_않는_조건은_빈_목록이다() throws Exception {
        mockMvc.perform(get("/hubs/stocks").param("productId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void 잘못된_페이지_값과_UUID_형식은_400이다() throws Exception {
        mockMvc.perform(get("/hubs/stocks").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/hubs/stocks").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/hubs/stocks").param("hubId", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    // ---------- 단건 ----------

    @Test
    void 단건_조회() throws Exception {
        UUID product = newProduct();
        String stockId = registerAndGetId(SEOUL, product, 30);

        mockMvc.perform(get("/hubs/stocks/{id}", stockId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockId").value(stockId))
                .andExpect(jsonPath("$.data.hubId").value(SEOUL.toString()))
                .andExpect(jsonPath("$.data.productId").value(product.toString()))
                .andExpect(jsonPath("$.data.quantity").value(30));
    }

    @Test
    void 없거나_삭제된_재고는_404이고_UUID_형식_오류는_400이다() throws Exception {
        mockMvc.perform(get("/hubs/stocks/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("재고를 찾을 수 없습니다."));

        UUID product = newProduct();
        String stockId = registerAndGetId(SEOUL, product, 1);
        jdbcTemplate.update("update p_stocks set deleted_at = now() where product_id = ?", product);
        mockMvc.perform(get("/hubs/stocks/{id}", stockId)).andExpect(status().isNotFound());

        mockMvc.perform(get("/hubs/stocks/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void 조회는_수량을_변경하지_않는다() throws Exception {
        UUID product = newProduct();
        String stockId = registerAndGetId(SEOUL, product, 12);

        mockMvc.perform(get("/hubs/stocks/{id}", stockId));
        mockMvc.perform(get("/hubs/stocks").param("productId", product.toString()));

        Integer quantity = jdbcTemplate.queryForObject(
                "select quantity from p_stocks where product_id = ?", Integer.class, product);
        assertThat(quantity).isEqualTo(12);
    }
}
