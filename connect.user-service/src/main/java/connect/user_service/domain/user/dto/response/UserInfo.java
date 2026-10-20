package connect.user_service.domain.user.dto.response;


import java.util.UUID;

public record UserInfo (
        UUID id,
        String slackId,
        String affiliationType,
        UUID affiliationId
){

}
