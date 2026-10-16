package connect.order_service.domain.client;

import connect.order_service.domain.client.Api.CompanyFeignApi;
import connect.order_service.domain.client.dto.response.CompanyInfo;
import connect.order_service.domain.client.clientInterface.CompanyClient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyFeignClient implements CompanyClient {
    private final CompanyFeignApi companyFeignApi;

    @Override
    public CompanyInfo getSupplyCompany(UUID supplierCompanyId) {
        return  companyFeignApi.getCompany(supplierCompanyId).getData();
    }

    @Override
    public void validateProduct(UUID supplierCompanyId, UUID productId) {
        companyFeignApi.getProduct(supplierCompanyId, productId);   // 성공하면 통과
    }
}
