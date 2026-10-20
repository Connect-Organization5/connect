package connect.delivery_service.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CreateDeliveryRequest {
    @NotNull(message = "주문 ID는 필수입니다.")
    private UUID orderId;
    @NotNull(message = "주문자 ID는 필수입니다.")
    private UUID ordererId;
    @NotNull(message = "출발지 HUB ID는 필수입니다.")
    private UUID departureHubId;
    @NotNull(message = "도착지 HUB ID는 필수입니다.")
    private UUID arrivalHubId;
    @NotBlank(message = "수령 주소는 필수입니다.")
    @Size(max = 200, message = "수령 주소는 200자 이하입니다.")
    private String deliveryAddress;
    @NotBlank(message = "수령인 이름은 필수입니다.")
    @Size(max = 50, message = "수령인 이름은 50자 이하입니다.")
    private String receiver;
}
