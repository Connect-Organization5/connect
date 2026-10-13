package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.CompanyInfo;

import java.util.UUID;

public interface CompanyClient {
    CompanyInfo getCompany(UUID companyId);
}
