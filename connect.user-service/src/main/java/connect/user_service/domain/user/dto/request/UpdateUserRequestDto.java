package connect.user_service.domain.user.dto.request;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// PUT 방식이므로 모든 필드 필수 (username, 소속은 변경 불가)
@Getter
@Setter
public class UpdateUserRequestDto {

    @NotBlank(message = "이메일을 입력하셔야 합니다.")
    @Email
    private String email;

    @NotNull(message = "비밀번호를 입력하셔야 합니다.")
    @Size(min = 8, max = 15, message = "최소 8자 이상, 15자 이하이며 알파벳 대소문자(a~z, A~Z), 숫자(0~9), 특수문자로 구성하셔야 합니다.")
    private String password;

    @NotBlank(message = "Slack ID를 입력하셔야 합니다.")
    private String slackId;

    @NotNull(message = "권한을 입력하셔야 합니다.")
    private UserRole role;

    // 소속은 변경 불가. DTO 에 없으면 JSON 에 보내도 조용히 무시되므로 필드로 받아서 값이 있으면 400 으로 거부
    @Null(message = "소속은 변경할 수 없습니다.")
    private AffiliationType affiliationType;

    @Null(message = "소속은 변경할 수 없습니다.")
    private Long affiliationId;
}
