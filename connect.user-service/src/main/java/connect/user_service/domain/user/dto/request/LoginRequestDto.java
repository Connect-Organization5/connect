package connect.user_service.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class LoginRequestDto {

    @NotBlank(message = "아이디를 입력하셔야 합니다.")
    private String username;

    @NotBlank(message = "비밀번호를 입력하셔야 합니다.")
    private String password;
}
