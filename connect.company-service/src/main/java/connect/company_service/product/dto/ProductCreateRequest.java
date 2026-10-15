package connect.company_service.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String name,

        @Size(max = 200, message = "상품 설명은 200자 이하여야 합니다.")
        String description,

        // DECIMAL(10,2): 정수부 8자리 + 소수부 2자리
        @NotNull(message = "가격은 필수입니다.")
        @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다.")
        @Digits(integer = 8, fraction = 2, message = "가격은 정수 8자리, 소수 2자리 이하여야 합니다.")
        BigDecimal price
) {
}
