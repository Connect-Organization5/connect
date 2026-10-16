package connect.company_service.product.service;

import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.dto.PagedResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import connect.company_service.product.dto.ProductCreateRequest;
import connect.company_service.product.dto.ProductResponse;
import connect.company_service.product.dto.ProductUpdateRequest;
import connect.company_service.product.entity.Product;
import connect.company_service.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private ProductService productService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private Company company;

    @BeforeEach
    void setUp() {
        // Company에는 생성 메서드가 없으므로 Mock으로 업체 관리자 userId만 지정
        company = mock(Company.class);
    }

    private Product savedProduct() {
        Product product = Product.create(company, "사과", "맛있는 사과", new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }

    @Test
    void 업체_관리자는_상품을_등록할_수_있다() {
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", productId);
            return product;
        });

        ProductResponse response = productService.createProduct(
                companyId, new ProductCreateRequest("사과", "맛있는 사과", new BigDecimal("1000")), ownerId, "USER");

        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.name()).isEqualTo("사과");
        assertThat(response.price()).isEqualByComparingTo("1000");
    }

    @Test
    void MASTER는_소유자가_아니어도_상품을_등록할_수_있다() {
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(
                companyId, new ProductCreateRequest("사과", null, new BigDecimal("1000")), UUID.randomUUID(), "MASTER");

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void 업체_관리자가_아니면_상품을_등록할_수_없다() {
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

        assertThatThrownBy(() -> productService.createProduct(
                companyId, new ProductCreateRequest("사과", null, new BigDecimal("1000")), UUID.randomUUID(), "USER"))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.FORBIDDEN.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    void 없는_업체에는_상품을_등록할_수_없다() {
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(
                companyId, new ProductCreateRequest("사과", null, new BigDecimal("1000")), ownerId, "USER"))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.COMPANY_NOT_FOUND.getMessage());
    }

    @Test
    void 다른_업체의_상품은_조회되지_않는다() {
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(companyId, productId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage());
    }

    @Test
    void 상품명이_있으면_업체_내부에서_부분_검색한다() {
        Pageable pageable = PageRequest.of(0, 10);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByCompany_IdAndNameContainingAndDeletedAtIsNull(companyId, "사과", pageable))
                .willReturn(new PageImpl<>(List.of(savedProduct()), pageable, 1));

        PagedResponse<ProductResponse> result = productService.getProducts(companyId, "사과", pageable);

        assertThat(result.contents()).extracting(ProductResponse::name).containsExactly("사과");
        verify(productRepository, never()).findByCompany_IdAndDeletedAtIsNull(any(), any());
    }

    @Test
    void 상품명이_없으면_업체_전체_상품을_조회한다() {
        Pageable pageable = PageRequest.of(0, 10);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByCompany_IdAndDeletedAtIsNull(companyId, pageable))
                .willReturn(new PageImpl<>(List.of(savedProduct()), pageable, 1));

        PagedResponse<ProductResponse> result = productService.getProducts(companyId, " ", pageable);

        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void 업체_관리자는_상품을_수정할_수_있다() {
        Product product = savedProduct();
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)).willReturn(Optional.of(product));

        ProductResponse response = productService.updateProduct(
                companyId, productId, new ProductUpdateRequest("배", "달콤한 배", new BigDecimal("2000")), ownerId, "USER");

        assertThat(response.name()).isEqualTo("배");
        assertThat(product.getPrice()).isEqualByComparingTo("2000");
    }

    @Test
    void 상품_삭제는_Soft_Delete로_처리된다() {
        Product product = savedProduct();
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)).willReturn(Optional.of(product));

        productService.deleteProduct(companyId, productId, ownerId, "USER");

        assertThat(product.getDeletedAt()).isNotNull();
        assertThat(product.getDeletedBy()).isEqualTo(ownerId);
        verify(productRepository, never()).delete(any());
    }

    @Test
    void 업체_관리자가_아니면_상품을_수정할_수_없다() {
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

        assertThatThrownBy(() -> productService.updateProduct(
                companyId, productId, new ProductUpdateRequest("배", null, new BigDecimal("2000")), UUID.randomUUID(), "USER"))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.FORBIDDEN.getMessage());
        // 권한 검증에서 막히므로 상품 조회까지 가지 않는다
        verify(productRepository, never()).findByIdAndCompany_IdAndDeletedAtIsNull(any(), any());
    }

    @Test
    void 업체_관리자가_아니면_상품을_삭제할_수_없다() {
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

        assertThatThrownBy(() -> productService.deleteProduct(companyId, productId, UUID.randomUUID(), "USER"))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.FORBIDDEN.getMessage());
        verify(productRepository, never()).findByIdAndCompany_IdAndDeletedAtIsNull(any(), any());
    }

    @Test
    void 존재하지_않는_상품은_수정할_수_없다() {
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(
                companyId, productId, new ProductUpdateRequest("배", null, new BigDecimal("2000")), ownerId, "USER"))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage());
    }

    @Test
    void 존재하지_않는_상품은_삭제할_수_없다() {
        given(company.getUserId()).willReturn(ownerId);
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(companyId, productId, ownerId, "USER"))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage());
    }

    @Test
    void MASTER는_소유자가_아니어도_상품을_수정할_수_있다() {
        Product product = savedProduct();
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)).willReturn(Optional.of(product));

        ProductResponse response = productService.updateProduct(
                companyId, productId, new ProductUpdateRequest("배", "달콤한 배", new BigDecimal("2000")), UUID.randomUUID(), "MASTER");

        assertThat(response.name()).isEqualTo("배");
        assertThat(product.getPrice()).isEqualByComparingTo("2000");
    }

    @Test
    void MASTER는_소유자가_아니어도_상품을_삭제할_수_있다() {
        Product product = savedProduct();
        UUID masterId = UUID.randomUUID();
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)).willReturn(Optional.of(product));

        productService.deleteProduct(companyId, productId, masterId, "MASTER");

        assertThat(product.getDeletedAt()).isNotNull();
        assertThat(product.getDeletedBy()).isEqualTo(masterId);
    }

    @Test
    void 업체의_활성_상품을_모두_soft_delete하고_ID_목록을_반환한다() {
        UUID deletedBy = UUID.randomUUID();
        Product first = Product.create(company, "사과", null, new BigDecimal("1000.00"));
        Product second = Product.create(company, "배", null, new BigDecimal("2000.00"));
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        ReflectionTestUtils.setField(first, "id", firstId);
        ReflectionTestUtils.setField(second, "id", secondId);
        given(productRepository.findAllByCompany_IdAndDeletedAtIsNull(companyId)).willReturn(List.of(first, second));

        List<UUID> deletedIds = productService.deleteProductsByCompany(companyId, deletedBy);

        assertThat(List.of(first, second)).allSatisfy(product -> {
            assertThat(product.getDeletedAt()).isNotNull();
            assertThat(product.getDeletedBy()).isEqualTo(deletedBy);
        });
        // 조회 결과에 정렬 조건이 없으므로 순서는 검증하지 않는다
        assertThat(deletedIds).containsExactlyInAnyOrder(firstId, secondId);
        verify(productRepository, never()).delete(any());
        verify(productRepository, never()).deleteAll(any());
    }

    @Test
    void 업체에_활성_상품이_없으면_빈_목록을_반환한다() {
        given(productRepository.findAllByCompany_IdAndDeletedAtIsNull(companyId)).willReturn(List.of());

        List<UUID> deletedIds = productService.deleteProductsByCompany(companyId, UUID.randomUUID());

        assertThat(deletedIds).isEmpty();
    }
}
