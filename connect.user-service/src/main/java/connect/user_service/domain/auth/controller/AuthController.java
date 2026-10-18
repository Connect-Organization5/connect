package connect.user_service.domain.auth.controller;

import connect.user_service.domain.auth.service.AuthService;
import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignUpRequestDto requestDto) {
        // Repository 에 존재하는지 확인
        userService.signup(requestDto);
        // user 생성 후 토큰 생성 후 return
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(authService.createAccessToken(requestDto)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> createAuthToken(@RequestBody LoginRequestDto requestDto) {
        log.info("###### username : " + requestDto.getUsername() + ", password : " +  requestDto.getPassword()+ " #####");
        // Repository 에 존재하는지 확인
        userService.login(requestDto);
        // user 조회 성공 시 토큰 생성 후 return
        return ResponseEntity.ok(new AuthResponse(authService.createAccessToken(requestDto)));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class AuthResponse {
        private String access_token;
    }
}
