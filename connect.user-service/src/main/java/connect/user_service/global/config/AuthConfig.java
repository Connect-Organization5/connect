package connect.user_service.global.config;

import connect.user_service.global.security.CustomAccessDeniedHandler;
import connect.user_service.global.security.CustomAuthenticationEntryPoint;
import connect.user_service.global.security.JwtAuthorizationFilter;
import connect.user_service.global.security.JwtUtil;
import connect.user_service.global.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AuthConfig {

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    // SecurityFilterChain 빈을 정의합니다. 이 메서드는 Spring Security 의 보안 필터 체인을 구성합니다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // .cors(AbstractHttpConfigurer::disable)
                // CSRF 보호를 비활성화합니다. CSRF 보호는 주로 브라우저 클라이언트를 대상으로 하는 공격을 방지하기 위해 사용됩니다.
                .csrf(AbstractHttpConfigurer::disable);
        //.httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화
        //.formLogin(AbstractHttpConfigurer::disable); // 폼 로그인 비활성화

        // 요청에 대한 접근 권한을 설정합니다.
        http
                .authorizeHttpRequests(authorize -> authorize
                        // /auth/login, /auth/signup 경로에 대한 접근을 허용합니다. 이 경로는 인증 없이 접근할 수 있습니다.
                        .requestMatchers("/auth/login", "/auth/signup").permitAll()
                        // 서비스 간 내부 호출(/internal/**)은 JWT 없이 허용. gateway 라우트에 없어서 외부에서는 접근할 수 없음
                        .requestMatchers("/internal/**").permitAll()
                        // Swagger UI, OpenAPI 문서는 JWT 없이 허용 (문서 조회만 가능, 실제 API 호출은 그대로 인증 필요)
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 그 외의 모든 요청은 인증이 필요합니다.
                        .anyRequest().authenticated()
                );

        // 세션 관리 정책을 정의합니다. 여기서는 세션을 사용하지 않도록 STATELESS 로 설정합니다.
        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        // 요청 헤더의 JWT 를 검증하고 SecurityContext 에 인증 정보를 저장합니다.
        http
                .addFilterBefore(new JwtAuthorizationFilter(jwtUtil, userDetailsService), UsernamePasswordAuthenticationFilter.class);

        // 인증 실패(401), 권한 부족(403) 시 ErrorResponse 형식으로 응답합니다.
        http
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                );

        // 설정된 보안 필터 체인을 반환합니다.
        return http.build();
    }

}
