package connect.delivery_service.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ChangeReceiverRequest {
    @NotBlank(message = "수령자 이름은 필수입니다.")
    @Size(max = 50, message = "수령자 이름은 50자 이하입니다.")
    private String receiver;
}
