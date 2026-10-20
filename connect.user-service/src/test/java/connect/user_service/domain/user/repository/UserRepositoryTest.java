package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.global.config.JpaAuditingConfig;
import connect.user_service.global.security.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// H2 인메모리 DB 로 실제 저장과 엔티티 제약 조건을 검증
@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");
    private static final UUID OTHER_AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000002000");

    // 테스트용 고정 UUID (idOf(1) → 00000000-0000-0000-0000-000000000001)
    private static UUID idOf(long no) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(no));
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User user(String username, String email, String slackId) {
        return new User(username, "encodedPw", email, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID, slackId);
    }

    @Test
    @DisplayName("회원 저장 후 DB 에서 조회")
    void save_and_find() {
        // when
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));

        // then
        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUsername()).isEqualTo("tester");
        assertThat(found.getPassword()).isEqualTo("encodedPw");
        assertThat(found.getEmail()).isEqualTo("tester@test.com");
        assertThat(found.getRole()).isEqualTo(UserRole.USER);
        assertThat(found.getAffiliationType()).isEqualTo(AffiliationType.COMPANY);
        assertThat(found.getAffiliationId()).isEqualTo(AFFILIATION_ID);
        assertThat(found.getSlackId()).isEqualTo("slack-1");
    }

    @Test
    @DisplayName("existsBy 메서드로 username, email, slackId 중복 확인")
    void existsBy() {
        // given
        userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));

        // then
        assertThat(userRepository.existsByUsername("tester")).isTrue();
        assertThat(userRepository.existsByUsername("other")).isFalse();
        assertThat(userRepository.existsByEmail("tester@test.com")).isTrue();
        assertThat(userRepository.existsByEmail("other@test.com")).isFalse();
        assertThat(userRepository.existsBySlackId("slack-1")).isTrue();
        assertThat(userRepository.existsBySlackId("slack-2")).isFalse();
    }

    @Test
    @DisplayName("같은 소속(affiliationType, affiliationId)의 회원 여러 명 저장 가능")
    void sameAffiliation_multipleUsers() {
        // when
        userRepository.saveAndFlush(user("tester1", "tester1@test.com", "slack-1"));
        userRepository.saveAndFlush(user("tester2", "tester2@test.com", "slack-2"));

        // then
        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("slackId 가 중복되면 DB 제약 조건 위반")
    void duplicateSlackId_violatesConstraint() {
        // given
        userRepository.saveAndFlush(user("tester1", "tester1@test.com", "slack-1"));

        // when & then
        assertThatThrownBy(() -> userRepository.saveAndFlush(user("tester2", "tester2@test.com", "slack-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("필수값(slackId)이 없으면 DB 제약 조건 위반")
    void nullRequiredField_violatesConstraint() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(user("tester", "tester@test.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsBy...AndIdNot 은 자기 자신을 제외하고 중복 확인")
    void existsByAndIdNot() {
        // given
        User user1 = userRepository.saveAndFlush(user("tester1", "tester1@test.com", "slack-1"));
        User user2 = userRepository.saveAndFlush(user("tester2", "tester2@test.com", "slack-2"));

        // then: 자기 값은 중복 아님, 다른 회원 값은 중복
        assertThat(userRepository.existsByEmailAndIdNot("tester1@test.com", user1.getId())).isFalse();
        assertThat(userRepository.existsByEmailAndIdNot("tester1@test.com", user2.getId())).isTrue();
        assertThat(userRepository.existsBySlackIdAndIdNot("slack-1", user1.getId())).isFalse();
        assertThat(userRepository.existsBySlackIdAndIdNot("slack-1", user2.getId())).isTrue();
    }

    @Test
    @DisplayName("로그인 없이 저장하면 createdAt, updatedAt 만 기록되고 createdBy, updatedBy 는 null")
    void auditing_onCreate_withoutLogin() {
        // when
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));

        // then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getUpdatedBy()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getDeletedBy()).isNull();
    }

    @Test
    @DisplayName("로그인 회원이 수정하면 updatedAt, updatedBy 가 기록되고 createdAt 은 그대로")
    void auditing_onUpdate_withLogin() {
        // given
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));
        LocalDateTime createdAt = saved.getCreatedAt();
        LocalDateTime firstUpdatedAt = saved.getUpdatedAt();

        User master = user("master", "master@test.com", "slack-master");
        master.setId(idOf(99));
        UserDetailsImpl userDetails = new UserDetailsImpl(master);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        // when
        saved.update("new@test.com", "newEncodedPw", "new-slack", UserRole.MASTER);
        userRepository.saveAndFlush(saved);

        // then
        assertThat(saved.getUpdatedBy()).isEqualTo(idOf(99));
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    // ===== 회원 목록 조회 =====

    private User member(String username, UserRole role, AffiliationType affiliationType, UUID affiliationId) {
        return new User(username, "encodedPw", username + "@test.com", role, affiliationType, affiliationId, "slack-" + username);
    }

    // Auditing 이 채운 created_at 을 원하는 값으로 바꿔서 정렬 검증 (null 이면 Auditing 적용 전 기존 회원)
    private void setCreatedAt(UUID id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE users SET created_at = ? WHERE id = ?")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
    }

    @Test
    @DisplayName("소속과 관계없이 USER, MASTER 모두 조회 - 삭제된 회원은 제외")
    void findAllOrderByRole_filters() {
        // given
        User master = userRepository.saveAndFlush(member("master", UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID));
        User user1 = userRepository.saveAndFlush(member("user1", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID));
        User otherType = userRepository.saveAndFlush(member("otherType", UserRole.USER, AffiliationType.HUB, AFFILIATION_ID));
        User otherId = userRepository.saveAndFlush(member("otherId", UserRole.USER, AffiliationType.COMPANY, OTHER_AFFILIATION_ID));
        User deleted = userRepository.saveAndFlush(member("deleted", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID));
        userRepository.softDeleteById(deleted.getId(), idOf(1), LocalDateTime.now());

        // when
        Page<User> result = userRepository.findAllOrderByRole(UserRole.MASTER, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).extracting(User::getId)
                .containsExactlyInAnyOrder(master.getId(), user1.getId(), otherType.getId(), otherId.getId());
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("잠금 조회 - 소속과 관계없이 MASTER 만 조회, USER, 삭제된 MASTER 는 제외")
    void findAllByRoleForUpdate_filters() {
        // given
        User master1 = userRepository.saveAndFlush(member("master1", UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID));
        User master2 = userRepository.saveAndFlush(member("master2", UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID));
        userRepository.saveAndFlush(member("user1", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID));
        User otherType = userRepository.saveAndFlush(member("otherType", UserRole.MASTER, AffiliationType.HUB, AFFILIATION_ID));
        User otherId = userRepository.saveAndFlush(member("otherId", UserRole.MASTER, AffiliationType.COMPANY, OTHER_AFFILIATION_ID));
        User deleted = userRepository.saveAndFlush(member("deleted", UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID));
        userRepository.softDeleteById(deleted.getId(), idOf(1), LocalDateTime.now());

        // when
        List<User> result = userRepository.findAllByRoleForUpdate(UserRole.MASTER);

        // then
        assertThat(result).extracting(User::getId)
                .containsExactlyInAnyOrder(master1.getId(), master2.getId(), otherType.getId(), otherId.getId());
    }

    @Test
    @DisplayName("정렬 - 소속과 관계없이 MASTER 먼저, 그다음 createdAt 내림차순, createdAt 이 없는 회원은 마지막")
    void findAllOrderByRole_sort() {
        // given: id 순서와 created_at 순서를 일부러 다르게 설정, 일부는 다른 소속
        User oldMaster = userRepository.saveAndFlush(member("oldMaster", UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID));
        User newUser = userRepository.saveAndFlush(member("newUser", UserRole.USER, AffiliationType.COMPANY, OTHER_AFFILIATION_ID));
        User legacyUser = userRepository.saveAndFlush(member("legacyUser", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID));
        User oldUser = userRepository.saveAndFlush(member("oldUser", UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID));
        User newMaster = userRepository.saveAndFlush(member("newMaster", UserRole.MASTER, AffiliationType.HUB, OTHER_AFFILIATION_ID));

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        setCreatedAt(oldMaster.getId(), base);
        setCreatedAt(newMaster.getId(), base.plusDays(3));
        setCreatedAt(oldUser.getId(), base.plusDays(1));
        setCreatedAt(newUser.getId(), base.plusDays(5));
        setCreatedAt(legacyUser.getId(), null);
        entityManager.clear();

        // when
        Page<User> result = userRepository.findAllOrderByRole(UserRole.MASTER, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactly("newMaster", "oldMaster", "newUser", "oldUser", "legacyUser");
    }

    @Test
    @DisplayName("페이지 나누기 - 정렬 순서대로 나뉘고 totalElements, totalPages 가 맞음")
    void findAllOrderByRole_paging() {
        // given: 회원 5명 (MASTER 1, USER 4)
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        User master = userRepository.saveAndFlush(member("master", UserRole.MASTER, AffiliationType.COMPANY, AFFILIATION_ID));
        setCreatedAt(master.getId(), base);
        for (int i = 1; i <= 4; i++) {
            User user = userRepository.saveAndFlush(member("user" + i, UserRole.USER, AffiliationType.COMPANY, AFFILIATION_ID));
            setCreatedAt(user.getId(), base.plusDays(i));
        }
        entityManager.clear();

        // when
        Page<User> first = userRepository.findAllOrderByRole(UserRole.MASTER, PageRequest.of(0, 2));
        Page<User> last = userRepository.findAllOrderByRole(UserRole.MASTER, PageRequest.of(2, 2));

        // then
        assertThat(first.getContent()).extracting(User::getUsername).containsExactly("master", "user4");
        assertThat(last.getContent()).extracting(User::getUsername).containsExactly("user1");
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
    }

    // ===== 논리적 삭제 =====

    private void loginAs(UUID userId) {
        User loginUser = user("login-" + userId, "login-" + userId + "@test.com", "slack-login-" + userId);
        loginUser.setId(userId);
        UserDetailsImpl userDetails = new UserDetailsImpl(loginUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    // 삭제된 행은 @SQLRestriction 때문에 JPA 로 조회되지 않으므로 네이티브 쿼리로 직접 조회
    private Object[] auditColumns(UUID id) {
        return (Object[]) entityManager.createNativeQuery(
                        "SELECT updated_at, updated_by, deleted_at, deleted_by FROM users WHERE id = ?")
                .setParameter(1, id)
                .getSingleResult();
    }

    // 네이티브 쿼리의 UUID 컬럼 값은 DB 에 따라 UUID(PostgreSQL) 또는 byte[16](H2) 으로 오므로 UUID 로 맞춰서 비교
    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        ByteBuffer buffer = ByteBuffer.wrap((byte[]) value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @Test
    @DisplayName("논리적 삭제 시 deleted_at, deleted_by 만 기록되고 updated_at, updated_by 는 그대로")
    void softDelete_doesNotChangeUpdatedColumns() {
        // given: idOf(50) 회원이 로그인한 상태에서 저장 → updated_by = idOf(50)
        loginAs(idOf(50));
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));
        Object[] before = auditColumns(saved.getId());

        // when: idOf(99) 회원이 로그인한 상태에서 삭제
        loginAs(idOf(99));
        int deleted = userRepository.softDeleteById(saved.getId(), idOf(99), LocalDateTime.now());

        // then
        Object[] after = auditColumns(saved.getId());
        assertThat(deleted).isEqualTo(1);
        assertThat(after[0]).isEqualTo(before[0]);                    // updated_at 그대로
        assertThat(toUuid(after[1])).isEqualTo(idOf(50));             // updated_by 그대로
        assertThat(after[2]).isNotNull();                             // deleted_at 기록
        assertThat(toUuid(after[3])).isEqualTo(idOf(99));             // deleted_by 기록
    }

    @Test
    @DisplayName("논리적 삭제된 회원은 조회, 중복 확인에서 제외")
    void softDelete_excludedFromQueries() {
        // given
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));

        // when
        userRepository.softDeleteById(saved.getId(), idOf(1), LocalDateTime.now());

        // then
        assertThat(userRepository.findById(saved.getId())).isEmpty();
        assertThat(userRepository.findByUsername("tester")).isEmpty();
        assertThat(userRepository.existsByUsername("tester")).isFalse();
        assertThat(userRepository.existsByEmail("tester@test.com")).isFalse();
        assertThat(userRepository.existsBySlackId("slack-1")).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미 삭제됐거나 없는 회원을 삭제하면 0 반환")
    void softDelete_alreadyDeletedOrMissing() {
        // given
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));
        userRepository.softDeleteById(saved.getId(), idOf(1), LocalDateTime.now());

        // when & then
        assertThat(userRepository.softDeleteById(saved.getId(), idOf(1), LocalDateTime.now())).isZero();
        assertThat(userRepository.softDeleteById(idOf(999), idOf(1), LocalDateTime.now())).isZero();
    }

    @Test
    @DisplayName("삭제된 회원의 username 은 DB unique 제약 때문에 재사용 불가 (409 DUPLICATE_RESOURCE 로 처리)")
    void softDelete_valuesStillOccupied() {
        // given
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));
        userRepository.softDeleteById(saved.getId(), idOf(1), LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> userRepository.saveAndFlush(user("tester", "other@test.com", "slack-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
}
