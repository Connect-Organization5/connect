package connect.company_service.product.controller;

import connect.company_service.global.dto.ApiResponse;
import connect.company_service.global.dto.PagedResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import connect.company_service.product.dto.ProductCreateRequest;
import connect.company_service.product.dto.ProductResponse;
import connect.company_service.product.dto.ProductUpdateRequest;
import connect.company_service.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// HTTP 요청 값을 추출해 ProductService에 전달만 한다. 권한/소유권 검증은 Service 담당
@RestController
@RequestMapping("/companies/{companyId}/products")
@RequiredArgsConstructor
public class ProductController {

    // Gateway가 JWT 검증 후 전달하는 사용자 정보 헤더
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @PathVariable UUID companyId,
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String role,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.createProduct(companyId, request, userId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(productService.getProduct(companyId, productId));
    }

    // name이 없으면 업체 전체 상품, 있으면 상품명 부분 일치 검색
    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getProducts(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String name,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        // @PageableDefault는 기본값만 정하므로 최대 size는 직접 검증
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(productService.getProducts(companyId, name, pageable));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String role,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(companyId, productId, request, userId, role));
    }

    // API 명세: 200 OK + 삭제 메시지 (기존 ApiResponse 사용)
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String role
    ) {
        productService.deleteProduct(companyId, productId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("삭제되었습니다.", null));
    }
}
