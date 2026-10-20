package connect.user_service.global.config;

import connect.user_service.domain.user.entity.AffiliationType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

// 소속별 초기 MASTER 계정 설정 (admin.masters[0].username ... 또는 환경변수 ADMIN_MASTERS_0_USERNAME ...)
// password 는 평문으로 받고 저장할 때 BCrypt 로 암호화
@ConfigurationProperties(prefix = "admin")
public record MasterAccountProperties(List<Account> masters) {

    public MasterAccountProperties {
        masters = (masters == null) ? List.of() : masters;
    }

    public record Account(
            String username,
            String password,
            String email,
            AffiliationType affiliationType,
            UUID affiliationId,
            String slackId
    ) {
    }
}
