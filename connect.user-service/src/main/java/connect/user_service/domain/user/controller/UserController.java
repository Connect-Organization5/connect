package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.UserInfoDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UpdateUserResponse;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.dto.PageResponse;
import connect.user_service.global.security.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UpdateUserResponse> getUser(@PathVariable UUID userId,
                                                      @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UpdateUserResponse response = userService.getUser(userId, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<UpdateUserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size 는 100 이하여야 합니다.") int size,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        PageResponse<UpdateUserResponse> response = userService.getAllUsers(userDetails.getUser(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UpdateUserResponse> updateUser(@PathVariable UUID userId,
                                                         @Valid @RequestBody UpdateUserRequestDto requestDto,
                                                         @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UpdateUserResponse response = userService.updateUser(userId, requestDto, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId,
                                           @AuthenticationPrincipal UserDetailsImpl userDetails) {
        userService.deleteUser(userId, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // 회원 관련 정보 받기
    @GetMapping("/user-info")
    @ResponseBody
    public UserInfoDto getUserInfo(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        String username = userDetails.getUser().getUsername();
        UserRole role = userDetails.getUser().getRole();
        boolean isAdmin = (role == UserRole.MASTER);

        return new UserInfoDto(username, isAdmin);
    }
}

