package connect.company_service.client.user.dto;

import java.util.UUID;

// User Service GET /internal/users/{userId} 응답의 data 중 관리자 지정 검증에 쓰는 부분
public record UserResponse(
        UUID id,
        String affiliationType,
        UUID affiliationId
) {
}
