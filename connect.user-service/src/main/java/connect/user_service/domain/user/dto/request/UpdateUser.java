package connect.user_service.domain.user.dto.request;

import connect.user_service.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UpdateUser {
    UserRole role;
    String affiliation;
    String slackId;
}
