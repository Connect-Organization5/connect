package connect.user_service.global.config;

import connect.user_service.global.security.UserDetailsImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

// TimeStamp 의 createdAt/By, updatedAt/By 를 자동으로 채움
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    // 현재 로그인한 회원의 id 를 createdBy, updatedBy 로 사용 (회원가입처럼 로그인 전이면 null)
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
                return Optional.ofNullable(userDetails.getUser().getId());
            }
            return Optional.empty();
        };
    }
}
