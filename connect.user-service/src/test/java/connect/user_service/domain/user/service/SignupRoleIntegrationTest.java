package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.entity.AffiliationCounterId;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.AffiliationCounterRepository;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 DB(H2)로 회원가입 시 MASTER 권한 부여 순서 검증
// 소속 카운터 생성은 별도 트랜잭션으로 커밋되고 동시성 테스트도 있으므로 테스트 트랜잭션(롤백)을 쓰지 않고 매번 직접 정리
@DataJpaTest
@Import({UserService.class, AffiliationCounterService.class, AffiliationCounterInitializer.class,
        JpaAuditingConfig.class, SignupRoleIntegrationTest.TestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SignupRoleIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4); // 테스트 속도를 위해 낮은 강도
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AffiliationCounterRepository affiliationCounterRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void cleanUp() {
        // 삭제된 회원(@SQLRestriction 으로 안 보임)까지 지우기 위해 네이티브 쿼리 사용
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM affiliation_counters").executeUpdate();
        });
    }

    private void signup(String username, AffiliationType affiliationType, Long affiliationId) {
        signup(username, affiliationType, affiliationId, "slack-" + username);
    }

    private void signup(String username, AffiliationType affiliationType, Long affiliationId, String slackId) {
        SignUpRequestDto request = new SignUpRequestDto();
        request.setUsername(username);
        request.setPassword("password1!");
        request.setEmail(username + "@test.com");
        request.setAffiliationType(affiliationType);
        request.setAffiliationId(affiliationId);
        request.setSlackId(slackId);
        userService.signup(request);
    }

    private UserRole roleOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getRole();
    }

    private long counterOf(AffiliationType affiliationType, Long affiliationId) {
        return affiliationCounterRepository.findById(new AffiliationCounterId(affiliationType, affiliationId))
                .orElseThrow().getMemberCount();
    }

    private void softDelete(String username) {
        Long id = userRepository.findByUsername(username).orElseThrow().getId();
        transactionTemplate.executeWithoutResult(status ->
                userRepository.softDeleteById(id, 1L, LocalDateTime.now()));
    }

    @Test
    @DisplayName("같은 소속에 7명이 차례로 가입하면 처음 5명은 MASTER, 나머지는 USER")
    void firstFiveAreMaster() {
        // when
        for (int i = 1; i <= 7; i++) {
            signup("user" + i, AffiliationType.COMPANY, 1000L);
        }

        // then
        for (int i = 1; i <= 5; i++) {
            assertThat(roleOf("user" + i)).as("user" + i).isEqualTo(UserRole.MASTER);
        }
        assertThat(roleOf("user6")).isEqualTo(UserRole.USER);
        assertThat(roleOf("user7")).isEqualTo(UserRole.USER);
        assertThat(counterOf(AffiliationType.COMPANY, 1000L)).isEqualTo(7);
    }

    @Test
    @DisplayName("소속 유형이나 소속 ID 가 다르면 가입 순서를 따로 셈")
    void countsPerAffiliation() {
        // given: COMPANY 1000 은 MASTER 5명이 다 참
        for (int i = 1; i <= 5; i++) {
            signup("company" + i, AffiliationType.COMPANY, 1000L);
        }

        // when
        signup("hubFirst", AffiliationType.HUB, 1000L);
        signup("otherIdFirst", AffiliationType.COMPANY, 2000L);
        signup("company6", AffiliationType.COMPANY, 1000L);

        // then
        assertThat(roleOf("hubFirst")).isEqualTo(UserRole.MASTER);
        assertThat(roleOf("otherIdFirst")).isEqualTo(UserRole.MASTER);
        assertThat(roleOf("company6")).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("삭제된 회원도 가입 순서에 포함 - MASTER 가 삭제돼도 다음 가입자는 USER")
    void deletedMembersStillCount() {
        // given
        for (int i = 1; i <= 5; i++) {
            signup("user" + i, AffiliationType.COMPANY, 1000L);
        }
        softDelete("user1");

        // when
        signup("user6", AffiliationType.COMPANY, 1000L);

        // then
        assertThat(roleOf("user6")).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("카운터 도입 전 기존 회원(삭제된 회원 포함)이 있으면 그 수부터 이어서 셈")
    void existingMembersBeforeCounter() {
        // given: 카운터 없이 저장된 기존 회원 3명 (그중 1명은 삭제됨)
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 1; i <= 3; i++) {
                userRepository.save(new User("legacy" + i, "encodedPw", "legacy" + i + "@test.com",
                        UserRole.USER, AffiliationType.HUB, 3000L, "slack-legacy" + i));
            }
        });
        softDelete("legacy1");

        // when: 4, 5, 6번째 가입자
        signup("new4", AffiliationType.HUB, 3000L);
        signup("new5", AffiliationType.HUB, 3000L);
        signup("new6", AffiliationType.HUB, 3000L);

        // then
        assertThat(roleOf("new4")).isEqualTo(UserRole.MASTER);
        assertThat(roleOf("new5")).isEqualTo(UserRole.MASTER);
        assertThat(roleOf("new6")).isEqualTo(UserRole.USER);
        assertThat(counterOf(AffiliationType.HUB, 3000L)).isEqualTo(6);
    }

    @Test
    @DisplayName("회원 저장이 실패하면 가입 순서 증가도 롤백")
    void failedSignupRollsBackCounter() {
        // given: 삭제된 회원의 slackId 는 중복 체크를 통과하지만 DB unique 제약에 걸림
        signup("user1", AffiliationType.COMPANY, 1000L);
        softDelete("user1");

        // when
        assertThatThrownBy(() -> signup("user2", AffiliationType.COMPANY, 1000L, "slack-user1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // then: 실패한 가입은 순서에 포함되지 않음
        assertThat(counterOf(AffiliationType.COMPANY, 1000L)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 소속에 10명이 동시에 가입해도 MASTER 는 정확히 5명")
    void concurrentSignups() throws Exception {
        // given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // when: 모든 스레드가 준비된 뒤 한꺼번에 가입 (소속 카운터 생성도 동시에 시도)
        for (int i = 1; i <= threadCount; i++) {
            String username = "user" + i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                signup(username, AffiliationType.COMPANY, 5000L);
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS); // 실패한 가입이 있으면 여기서 예외
        }
        executor.shutdown();

        // then
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= threadCount; i++) {
            users.add(userRepository.findByUsername("user" + i).orElseThrow());
        }
        assertThat(users).filteredOn(user -> user.getRole() == UserRole.MASTER).hasSize(5);
        assertThat(users).filteredOn(user -> user.getRole() == UserRole.USER).hasSize(5);
        assertThat(counterOf(AffiliationType.COMPANY, 5000L)).isEqualTo(10);
    }
}
