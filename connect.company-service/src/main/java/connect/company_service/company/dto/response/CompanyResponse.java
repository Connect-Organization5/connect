package connect.company_service.company.dto.response;

import connect.company_service.company.entity.Company;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        UUID userId,
        UUID hubId,
        String name,
        String address,
        LocalDateTime createdAt
) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getUserId(),
                company.getHubId(),
                company.getName(),
                company.getAddress(),
                company.getCreatedAt()
        );
    }
}
