package connect.company_service.company.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// MASTER가 업체 관리자를 지정할 때 보내는 요청 본문 (userId: 관리자로 지정할 업체 소속 사용자)
public record AssignManagerRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        UUID userId
) {
}
