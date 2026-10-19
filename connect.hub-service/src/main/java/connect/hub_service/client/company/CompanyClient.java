package connect.hub_service.client.company;

import connect.hub_service.client.company.dto.CompanyInfo;
import connect.hub_service.client.company.dto.ProductInfo;
import connect.hub_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// Eureka에 등록된 company-service의 조회 API를 호출한다. (Company DB 직접 접근 금지)
// 두 API 모두 헤더 없이 호출할 수 있고, 없거나 삭제된 대상이면 404 → FeignException.NotFound
@FeignClient(name = "company-service")
public interface CompanyClient {

    @GetMapping("/companies/{companyId}")
    ApiResponse<CompanyInfo> getCompany(@PathVariable("companyId") UUID companyId);

    // 다른 업체의 상품이어도 404다.
    @GetMapping("/companies/{companyId}/products/{productId}")
    ApiResponse<ProductInfo> getProduct(@PathVariable("companyId") UUID companyId,
                                        @PathVariable("productId") UUID productId);
}
