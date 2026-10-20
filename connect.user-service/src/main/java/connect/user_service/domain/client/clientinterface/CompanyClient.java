package connect.user_service.domain.client.clientinterface;

import connect.user_service.domain.user.dto.response.CompanyInfo;
import java.util.UUID;

public interface CompanyClient {
    CompanyInfo getCompany(UUID companyId);
}