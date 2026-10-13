package connect.order_service.domain.client;

import connect.order_service.domain.client.dto.CompanyInfo;
import connect.order_service.domain.client.clientInterface.CompanyClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

//@FeignClient(name = "company-service")
@Component
public class CompanyFeignClient implements CompanyClient {
    @Override
    public CompanyInfo getCompany(UUID companyId) {
        return new CompanyInfo(
                companyId,
                UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"),   // 관리 허브
                "서울특별시 강남구 테헤란로 1",
                UUID.fromString("cccccccc-0000-0000-0000-000000000001"));  // 업체 담당자
    }
}
