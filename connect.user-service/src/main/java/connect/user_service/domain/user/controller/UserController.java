package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.dto.UpdateUser;
import connect.user_service.domain.user.dto.UserInfoDto;
import connect.user_service.domain.user.dto.response.UserResponse;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        UserResponse response = userService.getUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers(@RequestParam int page, @RequestParam int size) {

        return null;
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(@RequestBody UpdateUser updateUser) {

        return null;
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID userId) {

        return ResponseEntity.status(HttpStatus.OK).build();
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

