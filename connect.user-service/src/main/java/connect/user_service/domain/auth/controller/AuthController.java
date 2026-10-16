package connect.user_service.domain.auth.controller;

import connect.user_service.domain.auth.service.AuthService;
import connect.user_service.domain.user.dto.request.SignUpRequest;
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

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignUpRequest request) {
        // Repository 에 존재하는지 확인

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(authService.createAccessToken(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> createAuthToken(@RequestBody SignUpRequest request) {
        log.info("###### username : " + request.getUsername() + ", password : " +  request.getPassword()+ " #####");
        // Repository 에 존재하는지 확인

        return ResponseEntity.ok(new AuthResponse(authService.createAccessToken(request)));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class AuthResponse {
        private String access_token;
    }

}
