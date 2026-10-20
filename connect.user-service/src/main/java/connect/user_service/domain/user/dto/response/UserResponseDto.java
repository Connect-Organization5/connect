package connect.user_service.domain.user.dto.response;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDto(
    UUID id,
    String username,
    String email,
    AffiliationType affiliationType,
    UUID affiliationId,
    String slackId,
    LocalDateTime createdAt
    ){

    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAffiliationType(),
                user.getAffiliationId(),
                user.getSlackId(),
                user.getCreatedAt()
        );
    }

}
