package connect.user_service.global.config;

import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 애플리케이션 시작 시 설정된 소속별 MASTER 계정이 없으면 비밀번호를 암호화해서 생성
// /auth/signup 은 MASTER 만 호출할 수 있으므로 각 소속의 첫 MASTER 는 여기서 만듦
@Slf4j(topic = "초기 MASTER 계정")
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(MasterAccountProperties.class)
public class MasterAccountInitializer implements ApplicationRunner {

    private final MasterAccountProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        for (MasterAccountProperties.Account account : properties.masters()) {
            validate(account);

            // 이미 있으면 덮어쓰지 않음 (재시작할 때마다 비밀번호가 바뀌지 않도록)
            if (userRepository.existsByUsername(account.username())) {
                log.info("이미 존재하는 계정이라 생성하지 않음: {}", account.username());
                continue;
            }

            User master = new User(
                    account.username(),
                    passwordEncoder.encode(account.password()),
                    account.email(),
                    UserRole.MASTER,
                    account.affiliationType(),
                    account.affiliationId(),
                    account.slackId()
            );
            userRepository.save(master);
            log.info("MASTER 계정 생성: {} ({} {})", account.username(), account.affiliationType(), account.affiliationId());
        }
    }

    // 설정이 빠졌으면 잘못된 계정이 저장되지 않도록 시작 단계에서 실패
    private void validate(MasterAccountProperties.Account account) {
        if (!StringUtils.hasText(account.username())
                || !StringUtils.hasText(account.password())
                || !StringUtils.hasText(account.email())
                || account.affiliationType() == null
                || account.affiliationId() == null
                || !StringUtils.hasText(account.slackId())) {
            throw new IllegalStateException("admin.masters 설정에 빠진 값이 있습니다: " + account.username());
        }
    }
}
