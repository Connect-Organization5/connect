package connect.user_service.domain.user.dto.request;

import connect.user_service.domain.user.entity.AffiliationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignUpRequestDto {

    @NotNull(message = "아이디를 입력하셔야 합니다.")
    @Size(min = 4, max = 10, message = "최소 4자 이상, 10자 이하이며 알파벳 소문자(a~z), 숫자(0~9)로 구성하셔야 합니다.")
    private String username;

    @NotNull(message = "비밀번호를 입력하셔야 합니다.")
    @Size(min = 8, max = 15, message = "최소 8자 이상, 15자 이하이며 알파벳 대소문자(a~z, A~Z), 숫자(0~9), 특수문자로 구성하셔야 합니다.")
    private String password;

    @NotBlank
    @Email
    private String email;

    @NotNull(message = "소속 유형을 입력하셔야 합니다. (COMPANY, HUB)")
    private AffiliationType affiliationType;

    @NotNull(message = "소속 ID를 입력하셔야 합니다.")
    private Long affiliationId;

    @NotBlank(message = "Slack ID를 입력하셔야 합니다.")
    private String slackId;
}
