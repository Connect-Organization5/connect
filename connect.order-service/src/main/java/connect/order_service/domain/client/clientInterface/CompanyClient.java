package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.response.CompanyInfo;

import java.util.UUID;

public interface CompanyClient {
    void validateProduct(UUID supplierCompanyId, UUID productId);

    CompanyInfo getSupplyCompany(UUID supplierCompanyId);
}
