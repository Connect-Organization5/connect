package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.CompanyInfo;
import connect.order_service.domain.client.dto.ProductInfo;

import java.util.UUID;

public interface CompanyClient {
    CompanyInfo getCompany(UUID userId);

    ProductInfo getProduct(UUID productId);

    CompanyInfo getSupplyCompany(UUID supplierCompanyId);
}
