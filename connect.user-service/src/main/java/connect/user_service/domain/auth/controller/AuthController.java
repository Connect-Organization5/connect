package connect.user_service.domain.auth.controller;

import connect.user_service.domain.auth.controller.docs.AuthControllerDocs;
import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.service.UserService;
import connect.user_service.global.dto.ApiResponse;
import connect.user_service.global.security.JwtUtil;
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
public class AuthController implements AuthControllerDocs {

    private final JwtUtil jwtUtil;

    private final UserService userService;

    /*@GetMapping("/signup")
    public ResponseEntity<ApiResponse<Map<String, ?>>> signup() {
        Map<String, ?> companyAndHub = userService.signup();
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("새로운 회원님, 반갑습니다.", companyAndHub));
    }*/

    // 로그인 없이 회원가입 (USER 권한으로 생성, 토큰은 발급하지 않으므로 가입 후 /auth/login 으로 로그인)
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponseDto>> signup(@RequestBody SignUpRequestDto requestDto) { // @Valid 는 AuthControllerDocs 에 선언 (구현 메서드에 다시 선언하면 HV000151 오류)
        UserResponseDto response = userService.signup(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("회원가입이 완료되었습니다.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequestDto requestDto) { // @Valid 는 AuthControllerDocs 에 선언 (구현 메서드에 다시 선언하면 HV000151 오류)
        // 아이디, 비밀번호 확인
        User user = userService.login(requestDto);
        // 확인된 회원 정보로 토큰 생성 후 return (토큰은 data.access_token)
        AuthResponse response = new AuthResponse(jwtUtil.createAccessToken(user));
        return ResponseEntity.ok(ApiResponse.ok("로그인에 성공했습니다.", response));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuthResponse {
        private String access_token;
    }
}
