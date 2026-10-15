package connect.order_service.domain.client.dto;


import java.util.UUID;

public record UserInfo (
        UUID userId,
        String slackId,
        String affiliationType,
        UUID affiliationId
){

}
