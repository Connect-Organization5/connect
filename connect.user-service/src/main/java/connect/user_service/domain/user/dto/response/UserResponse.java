package connect.user_service.domain.user.dto.response;

import connect.user_service.domain.user.entity.User;

public record UserResponse (
    Long id,
    String username,
    String password,
    String email,
    String affiliationType,
    Long affiliationId,
    String slackId
    ){

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getEmail(),
                user.getAffiliationType(),
                user.getAffiliationId(),
                user.getSlackId()
        );
    }

}
