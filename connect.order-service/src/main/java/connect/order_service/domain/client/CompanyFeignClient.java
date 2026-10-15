package connect.order_service.domain.client;

import connect.order_service.domain.client.dto.CompanyInfo;
import connect.order_service.domain.client.clientInterface.CompanyClient;
import connect.order_service.domain.client.dto.ProductInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

//@FeignClient(name = "company-service")
@Component
public class CompanyFeignClient implements CompanyClient {
    @Override
    public CompanyInfo getCompany(UUID userId) {
        return new CompanyInfo(
                UUID.fromString("bbbbbbbb-9999-9999-9999-bbbbbbbbbbbb"),
                UUID.fromString("bbbbbbbb-0000-0000-0000-bbbbbbbbbbbb"),   // 관리 허브
                "서울특별시 강남구 테헤란로 1",
                UUID.fromString("bbbbbbbb-0000-0000-0000-bbbbbbbbbbbb"));  // 업체 담당자
    }

    @Override
    public CompanyInfo getSupplyCompany(UUID supplierCompanyId) {
        return new CompanyInfo(
                UUID.fromString("cccccccc-9999-9999-9999-cccccccccccc"),
                UUID.fromString("cccccccc-0000-0000-0000-cccccccccccc"),   // 관리 허브
                "부산 광역시 강남구 테헤란로 1",
                UUID.fromString("cccccccc-0000-0000-0000-cccccccccccc"));  // 업체 담당자
    }

    @Override
    public ProductInfo getProduct(UUID productId) {
        return new ProductInfo(
                productId,
                UUID.fromString("cccccccc-9999-9999-9999-cccccccccccc"));   // 공급 업체
    }
}
