package connect.order_service.domain.client.dto.response;

import java.util.UUID;

public record CompanyInfo (
    UUID id,
    UUID userId,
    UUID hubId,
    String name,
    String address
){

}
