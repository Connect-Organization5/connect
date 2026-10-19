package connect.user_service.domain.auth.controller;

import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.security.JwtUtil;
import jakarta.validation.Valid;
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

    private final JwtUtil jwtUtil;

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequestDto requestDto) {
        // Repository 에 존재하는지 확인
        userService.signup(requestDto);
        // user 생성 후 토큰 생성 후 return
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(jwtUtil.createAccessToken(requestDto.getUsername())));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDto requestDto) {
        // 아이디, 비밀번호 확인
        User user = userService.login(requestDto);
        // 확인된 회원 정보로 토큰 생성 후 return
        return ResponseEntity.ok(new AuthResponse(jwtUtil.createAccessToken(user.getUsername())));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class AuthResponse {
        private String access_token;
    }
}
