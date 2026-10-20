package connect.delivery_service.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ChangeDeliveryUserRequest {
        @NotNull(message = "담당자 ID는 필수입니다.")
        private UUID deliveryUserId;

}
