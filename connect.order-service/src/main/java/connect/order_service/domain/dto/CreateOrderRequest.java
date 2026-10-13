package connect.order_service.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CreateOrderRequest {
    @NotNull(message = "상품 ID는 필수입니다.")
    private UUID productId;

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 1개 이상이어야 합니다.")
    private Integer quantity;

    @Size(max = 200, message = "요청사항은 200자 이하로 입력해주세요.")
    private String requestMessage;

    @NotBlank(message = "수령인은 필수입니다.")
    @Size(max = 50, message = "수령인은 50자 이하로 입력해주세요.")
    private String receiver;
}
