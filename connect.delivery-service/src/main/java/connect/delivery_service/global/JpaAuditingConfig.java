package connect.delivery_service.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    // TODO 인증 연동 전 임시값 -> 게이트웨이가 넘겨주는 사용자 id 로 교체
    private static final UUID TEMP_AUDITOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> Optional.of(TEMP_AUDITOR_ID);
    }
}
