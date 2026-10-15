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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    // Gateway가 X-User-Role 헤더로 전달하는 관리자 Role
    private static final String MASTER_ROLE = "MASTER";

    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public ProductResponse createProduct(UUID companyId, ProductCreateRequest request, UUID userId, String role) {
        Company company = getCompany(companyId);
        validateCompanyOwner(company, userId, role);

        Product product = Product.create(company, request.name(), request.description(), request.price());
        return ProductResponse.from(productRepository.save(product));
    }

    public ProductResponse getProduct(UUID companyId, UUID productId) {
        getCompany(companyId);
        return ProductResponse.from(getProductInCompany(companyId, productId));
    }

    // name이 없으면 Company 전체 상품, 있으면 Company 내부 상품명 부분 일치 검색
    public PagedResponse<ProductResponse> getProducts(UUID companyId, String name, Pageable pageable) {
        getCompany(companyId);

        Page<Product> products = StringUtils.hasText(name)
                ? productRepository.findByCompany_IdAndNameContainingAndDeletedAtIsNull(companyId, name.trim(), pageable)
                : productRepository.findByCompany_IdAndDeletedAtIsNull(companyId, pageable);

        return PagedResponse.from(products.map(ProductResponse::from));
    }

    @Transactional
    public ProductResponse updateProduct(UUID companyId, UUID productId, ProductUpdateRequest request, UUID userId, String role) {
        Company company = getCompany(companyId);
        validateCompanyOwner(company, userId, role);

        Product product = getProductInCompany(companyId, productId);
        // 변경 감지(Dirty Checking)로 트랜잭션 커밋 시 UPDATE 실행
        product.update(request.name(), request.description(), request.price());
        return ProductResponse.from(product);
    }

    @Transactional
    public void deleteProduct(UUID companyId, UUID productId, UUID userId, String role) {
        Company company = getCompany(companyId);
        validateCompanyOwner(company, userId, role);

        Product product = getProductInCompany(companyId, productId);
        product.delete(userId);
    }

    private Company getCompany(UUID companyId) {
        return companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));
    }

    // 다른 Company 소속 상품이면 조회되지 않으므로 PRODUCT_NOT_FOUND
    private Product getProductInCompany(UUID companyId, UUID productId) {
        return productRepository.findByIdAndCompany_IdAndDeletedAtIsNull(productId, companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    // 업체 관리자(Company.userId == 로그인 userId) 또는 MASTER만 허용
    private void validateCompanyOwner(Company company, UUID userId, String role) {
        if (MASTER_ROLE.equals(role)) {
            return;
        }
        if (userId == null || !userId.equals(company.getUserId())) {
            throw new BaseException(ErrorCode.FORBIDDEN);
        }
    }
}
