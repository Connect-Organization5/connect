package connect.user_service.domain.client.Api;

import connect.user_service.domain.user.dto.response.CompanyInfo;
import connect.user_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "company-service")
public interface CompanyFeignApi {

    @GetMapping("/companies/{companyId}/products/{productId}")
    void getProduct(@PathVariable("companyId") UUID companyId,
                               @PathVariable("productId") UUID productId);

    @GetMapping("/companies/{companyId}")
    ApiResponse<CompanyInfo> getCompany(@PathVariable("companyId") UUID companyId);
}
