package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.controller.docs.UserControllerDocs;
import connect.user_service.domain.user.dto.UserInfoDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.dto.ApiResponse;
import connect.user_service.global.dto.PageResponse;
import connect.user_service.global.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponseDto>> getUser(@PathVariable UUID userId,
                                                                @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UserResponseDto response = userService.getUser(userId, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("회원 정보를 조회했습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponseDto>>> getAllUsers(
            // page·size 범위 검증(@Min/@Max)은 UserControllerDocs 에 선언 (구현 메서드에 다시 선언하면 Hibernate Validator HV000151 오류)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        PageResponse<UserResponseDto> response = userService.getAllUsers(userDetails.getUser(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("회원 목록을 조회했습니다.", response));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateUser(@PathVariable UUID userId,
                                                                   @RequestBody UpdateUserRequestDto requestDto, // @Valid 는 UserControllerDocs 에 선언 (구현 메서드에 다시 선언하면 HV000151 오류)
                                                                   @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UserResponseDto response = userService.updateUser(userId, requestDto, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("회원 정보를 수정했습니다.", response));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId,
                                           @AuthenticationPrincipal UserDetailsImpl userDetails) {
        userService.deleteUser(userId, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // 회원 관련 정보 받기
    @GetMapping("/user-info")
    public ResponseEntity<ApiResponse<UserInfoDto>> getUserInfo(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        String username = userDetails.getUser().getUsername();
        UserRole role = userDetails.getUser().getRole();
        boolean isAdmin = (role == UserRole.MASTER);

        return ResponseEntity.ok(ApiResponse.ok("로그인 회원 정보를 조회했습니다.", new UserInfoDto(username, isAdmin)));
    }
}

