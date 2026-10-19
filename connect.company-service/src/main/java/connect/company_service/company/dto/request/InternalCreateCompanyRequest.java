package connect.company_service.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// 회원가입 시 user-service가 새 업체를 만들 때 보내는 요청 본문 (userId: 가입하는 사용자, 업체 관리자가 됨)
public record InternalCreateCompanyRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        UUID userId,

        @NotNull(message = "허브 ID는 필수입니다.")
        UUID hubId,

        @NotBlank(message = "업체명은 필수입니다.")
        @Size(max = 100, message = "업체명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
        String address
) {
}
