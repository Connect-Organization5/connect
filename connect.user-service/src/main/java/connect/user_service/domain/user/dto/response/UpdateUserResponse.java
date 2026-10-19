package connect.user_service.domain.user.dto.response;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;

import java.util.UUID;

public record UpdateUserResponse(
    UUID id,
    String username,
    String email,
    AffiliationType affiliationType,
    UUID affiliationId,
    String slackId
    ){

    public static UpdateUserResponse from(User user) {
        return new UpdateUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAffiliationType(),
                user.getAffiliationId(),
                user.getSlackId()
        );
    }

}
