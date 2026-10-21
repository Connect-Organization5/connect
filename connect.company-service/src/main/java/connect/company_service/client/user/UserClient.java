package connect.company_service.client.user;

import connect.company_service.client.user.dto.UserResponse;
import connect.company_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

// Eureka에 등록된 user-service를 호출 (User DB 직접 접근 금지)
@FeignClient(name = "user-service")
public interface UserClient {

    // 존재하지 않는 사용자면 User Service가 404 → FeignException.NotFound 발생
    @GetMapping("/internal/users/{userId}")
    ApiResponse<UserResponse> getUser(
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader("X-User-Role") String requesterRole,
            @PathVariable("userId") UUID userId
    );
}
