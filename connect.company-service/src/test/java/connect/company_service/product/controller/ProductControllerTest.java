package connect.company_service.product.controller;

import connect.company_service.global.dto.PagedResponse;
import connect.company_service.product.dto.ProductCreateRequest;
import connect.company_service.product.dto.ProductResponse;
import connect.company_service.product.dto.ProductUpdateRequest;
import connect.company_service.product.entity.Product;
import connect.company_service.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller만 로딩하고 ProductService는 Mock으로 대체 (DB 사용 안 함)
@WebMvcTest(ProductController.class)
// 메인 클래스의 @EnableJpaAuditing이 요구하는 JPA 매핑 정보를 Mock으로 대체
@MockitoBean(types = JpaMetamodelMappingContext.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private final ProductResponse response =
            new ProductResponse(productId, "사과", "맛있는 사과", new BigDecimal("1000"));

    private static final String PRODUCT_JSON = """
            {"name": "사과", "description": "맛있는 사과", "price": 1000}
            """;

    private String baseUrl() {
        return "/companies/" + companyId + "/products";
    }

    @Test
    void 상품_등록_성공시_201을_반환한다() throws Exception {
        given(productService.createProduct(eq(companyId), any(ProductCreateRequest.class), eq(userId), eq("USER")))
                .willReturn(response);

        mockMvc.perform(post(baseUrl())
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.name").value("사과"))
                .andExpect(jsonPath("$.price").value(1000))
                .andExpect(jsonPath("$.companyId").doesNotExist());
    }

    @Test
    void 상품명이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(baseUrl())
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": 1000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("상품명은 필수입니다."));

        verifyNoInteractions(productService);
    }

    @Test
    void 필수_헤더가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(baseUrl())
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void 상품_단건_조회_성공시_200을_반환한다() throws Exception {
        given(productService.getProduct(companyId, productId)).willReturn(response);

        mockMvc.perform(get(baseUrl() + "/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.description").value("맛있는 사과"));
    }

    @Test
    void 상품_목록은_기본_페이지_조건으로_조회한다() throws Exception {
        given(productService.getProducts(eq(companyId), eq(null), any(Pageable.class)))
                .willReturn(new PagedResponse<>(List.of(response), 1, 1, 10, 0));

        mockMvc.perform(get(baseUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contents[0].name").value("사과"))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).getProducts(eq(companyId), eq(null), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 상품명_검색어를_Service에_전달한다() throws Exception {
        given(productService.getProducts(eq(companyId), eq("사과"), any(Pageable.class)))
                .willReturn(new PagedResponse<>(List.of(response), 1, 1, 10, 0));

        mockMvc.perform(get(baseUrl()).param("name", "사과").param("page", "0").param("size", "10"))
                .andExpect(status().isOk());

        verify(productService).getProducts(eq(companyId), eq("사과"), any(Pageable.class));
    }

    @Test
    void size가_100을_넘으면_400을_반환한다() throws Exception {
        mockMvc.perform(get(baseUrl()).param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void 존재하지_않는_필드로_정렬하면_400을_반환한다() throws Exception {
        // 실제로는 Repository가 정렬 쿼리를 만들 때 던지는 예외. Service Mock으로 재현한다
        given(productService.getProducts(eq(companyId), eq(null), any(Pageable.class)))
                .willThrow(new PropertyReferenceException("foo", TypeInformation.of(Product.class), List.of()));

        mockMvc.perform(get(baseUrl()).param("sort", "foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 상품_수정_성공시_200을_반환한다() throws Exception {
        ProductResponse updated = new ProductResponse(productId, "배", "달콤한 배", new BigDecimal("2000"));
        given(productService.updateProduct(eq(companyId), eq(productId), any(ProductUpdateRequest.class), eq(userId), eq("MASTER")))
                .willReturn(updated);

        mockMvc.perform(put(baseUrl() + "/" + productId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "배", "description": "달콤한 배", "price": 2000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("배"))
                .andExpect(jsonPath("$.price").value(2000));
    }

    @Test
    void 상품_삭제_성공시_200과_삭제_메시지를_반환한다() throws Exception {
        mockMvc.perform(delete(baseUrl() + "/" + productId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("삭제되었습니다."));

        verify(productService).deleteProduct(companyId, productId, userId, "USER");
    }
}
