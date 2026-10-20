package connect.order_service.domain.client.dto.response;


import java.util.UUID;

public record UserInfo (
        UUID id,
        String slackId,
        String affiliationType,
        UUID affiliationId
){

}
