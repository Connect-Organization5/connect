package connect.company_service.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCompanyRequest(
        @NotNull(message = "허브 ID는 필수입니다.")
        UUID hubId,

        // MASTER만 사용
        @Size(max = 100, message = "업체명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
        String address
) {
}
