package connect.company_service.company.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// user-service가 사용자의 소속을 업체로 지정할 때 보내는 요청 본문
public record AssignManagerRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        UUID userId
) {
}
