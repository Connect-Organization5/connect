package connect.delivery_service.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ArriveHubRequest {
    @NotNull(message = "실제 이동거리는 필수입니다.")
    @DecimalMin(value = "0", message = "실제 이동거리는 0 이상이어야 합니다.")
    @Digits(integer = 8, fraction = 2, message = "실제 이동거리는 정수 8자리, 소수 2자리까지 입력할 수 있습니다.")
    private BigDecimal actualDistance;
}
