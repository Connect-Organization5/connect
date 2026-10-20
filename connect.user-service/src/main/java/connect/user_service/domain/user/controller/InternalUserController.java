package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    // 다른 Service 에 User 본인 데이터 보내기
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponseDto>> sendUser(@PathVariable UUID userId) {
        UserResponseDto response = userService.sendUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("회원 정보를 조회했습니다.", response));
    }

    @GetMapping("/hub/{departureHubId}/delivery-users")
    public ApiResponse<List<UUID>> getHubDeliveryUserIds(@PathVariable UUID departureHubId) {
        List<UUID> response = userService.getHubDeliveryUserIds(departureHubId);
        return ApiResponse.ok("소속 허브 사용자들을 조회했습니다.", response);
    }
}
