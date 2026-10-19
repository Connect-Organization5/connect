package connect.order_service.global.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

// 게이트웨이가 넘겨준 X-User-Id를 createdBy / updatedBy로 사용
@Component
public class HeaderAuditorAware implements AuditorAware<UUID> {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(attrs -> ((ServletRequestAttributes) attrs).getRequest().getHeader(USER_ID_HEADER))
                .map(UUID::fromString);
    }
}
