package connect.order_service.domain.client.dto;

import java.util.UUID;

public record CompanyInfo (
    UUID companyId,
    UUID hubId,
    String address,
    UUID managerId
){

}
