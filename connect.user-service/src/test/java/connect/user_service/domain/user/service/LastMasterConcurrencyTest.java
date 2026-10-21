package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 마지막 MASTER 보호(isLastMaster + SELECT ... FOR UPDATE)가 실제 동시 요청에서도 지켜지는지 H2 DB 로 확인
// 서로 다른 MASTER 행을 동시에 삭제/강등하는 상황이라 같은 행 충돌만 잡는 낙관적 락으로는 막을 수 없는 경우
@SpringBootTest
@ActiveProfiles("test")
class LastMasterConcurrencyTest {

    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");
    private static final int ROUNDS = 100;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        // .env 의 초기 MASTER 계정(MasterAccountInitializer)이 MASTER 수에 섞이지 않도록 비움
        clearUsers();
        executor =Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        clearUsers();
    }

    @Test
    @DisplayName("잠금 없이 MASTER 수를 세고 삭제하면 서로를 동시에 삭제했을 때 MASTER 가 0명이 됨 (문제 재현)")
    void withoutLock_deleteEachOther_noMasterLeft() throws Exception {
        // given
        User a = saveMaster("a");
        User b = saveMaster("b");
        // 두 트랜잭션이 모두 MASTER 수를 센 뒤에 삭제하도록 맞춤 (검사와 삭제 사이에 다른 요청이 끼어드는 상황)
        CyclicBarrier bothChecked = new CyclicBarrier(2);

        // when
        Future<Boolean> deleteB = executor.submit(() -> deleteWithoutLock(b, a, bothChecked));
        Future<Boolean> deleteA = executor.submit(() -> deleteWithoutLock(a, b, bothChecked));

        // then: 둘 다 "MASTER 가 2명이니 삭제 가능" 으로 판단해서 둘 다 삭제됨
        assertThat(deleteB.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(deleteA.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(countMasters()).isZero();
    }

    @Test
    @DisplayName("MASTER 2명이 서로를 동시에 삭제해도 한 명만 삭제되고 MASTER 가 1명 남음 (100회 반복)")
    void deleteEachOther_concurrently_oneMasterRemains() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given
            User a = saveMaster("a" + round);
            User b = saveMaster("b" + round);

            // when
            List<Object> results = runConcurrently(
                    () -> userService.deleteUser(b.getId(), a),
                    () -> userService.deleteUser(a.getId(), b)
            );

            // then
            assertOneSucceededOneRejected(results, ErrorCode.LAST_MASTER_CANNOT_BE_DELETED, round);
            assertThat(countMasters()).as("round %d", round).isEqualTo(1);

            clearUsers();
        }
    }

    @Test
    @DisplayName("한 MASTER 강등과 다른 MASTER 삭제가 동시에 들어와도 MASTER 가 1명 남음 (100회 반복)")
    void demoteAndDelete_concurrently_oneMasterRemains() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // given
            User a = saveMaster("a" + round);
            User b = saveMaster("b" + round);

            // when: a 가 b 를 USER 로 강등, 동시에 b 가 a 를 삭제
            List<Object> results = runConcurrently(
                    () -> userService.updateUser(b.getId(), demoteRequest(b), a),
                    () -> userService.deleteUser(a.getId(), b)
            );

            // then: 먼저 잠금을 얻은 요청만 성공하고, 나중 요청은 마지막 MASTER 라서 거부됨
            long rejected = results.stream().filter(BaseException.class::isInstance).count();
            assertThat(rejected).as("round %d", round).isEqualTo(1);
            assertThat(results).as("round %d", round)
                    .filteredOn(BaseException.class::isInstance)
                    .extracting("errorCode")
                    .containsAnyOf(ErrorCode.LAST_MASTER_CANNOT_BE_DEMOTED, ErrorCode.LAST_MASTER_CANNOT_BE_DELETED);
            assertThat(countMasters()).as("round %d", round).isEqualTo(1);

            clearUsers();
        }
    }

    // 잠금 없이 MASTER 수를 센 뒤 삭제하는 예전 방식 (비교용). 삭제했으면 true
    private boolean deleteWithoutLock(User target, User requester, CyclicBarrier bothChecked) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            long masters = userRepository.findAll().stream()
                    .filter(user -> user.getRole() == UserRole.MASTER)
                    .count();
            await(bothChecked);
            if (masters <= 1) {
                return false;
            }
            return userRepository.softDeleteById(target.getId(), requester.getId(), LocalDateTime.now()) == 1;
        }));
    }

    // 두 작업을 같은 시점에 시작시키고 각 결과(성공이면 반환값 또는 "OK", 실패면 예외)를 반환
    private List<Object> runConcurrently(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Runnable task : List.of(first, second)) {
            Callable<Object> call = () -> {
                start.await();
                try {
                    task.run();
                    return "OK";
                } catch (BaseException e) {
                    return e;
                }
            };
            futures.add(executor.submit(call));
        }
        start.countDown();

        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        return results;
    }

    private void assertOneSucceededOneRejected(List<Object> results, ErrorCode expected, int round) {
        assertThat(results).as("round %d", round).containsOnlyOnce("OK");
        assertThat(results).as("round %d", round)
                .filteredOn(BaseException.class::isInstance)
                .singleElement()
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private User saveMaster(String name) {
        return userRepository.saveAndFlush(new User(name, "encodedPw", name + "@test.com", UserRole.MASTER,
                AffiliationType.COMPANY, AFFILIATION_ID, "slack-" + name));
    }

    private UpdateUserRequestDto demoteRequest(User target) {
        UpdateUserRequestDto request = new UpdateUserRequestDto();
        request.setEmail(target.getEmail());
        request.setPassword("newPassword1!");
        request.setSlackId(target.getSlackId());
        request.setRole(UserRole.USER);
        return request;
    }

    private long countMasters() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.MASTER)
                .count();
    }

    // deleteAllInBatch 는 @SQLRestriction 때문에 논리 삭제된 행을 남기므로 SQL 로 물리 삭제 (다음 라운드 unique 충돌 방지)
    private void clearUsers() {
        jdbcTemplate.update("DELETE FROM users");
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
