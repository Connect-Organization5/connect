package connect.hub_service.global.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

// Gateway가 전달한 X-User-Id를 createdBy / updatedBy 등 감사 값으로 사용
@Component
public class HeaderAuditorAware implements AuditorAware<UUID> {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(attrs -> ((ServletRequestAttributes) attrs).getRequest().getHeader(USER_ID_HEADER))
                .flatMap(HeaderAuditorAware::parseUuid);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
