package connect.user_service.domain.user.dto.response;

import java.util.UUID;

public record CompanyInfo (
    UUID id,
    UUID userId,
    UUID hubId,
    String name,
    String address
){

}
