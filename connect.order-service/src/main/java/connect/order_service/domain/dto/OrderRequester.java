package connect.order_service.domain.dto;

import connect.order_service.domain.client.dto.CompanyInfo;
import connect.order_service.domain.client.dto.HubInfo;

import java.util.UUID;

public record OrderRequester(
        UUID companyId,
        UUID arrivalHubId,
        String address
) {
    public static OrderRequester from(CompanyInfo company) {
        return new OrderRequester(company.companyId(), company.hubId(), company.address());
    }

    public static OrderRequester from(HubInfo hub) {
        return new OrderRequester(hub.hubId(), hub.hubId(), hub.address());
    }
}
