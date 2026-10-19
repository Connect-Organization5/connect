package connect.user_service.domain.user.repository;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// H2 인메모리 DB 로 실제 저장과 엔티티 제약 조건을 검증
@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User user(String username, String email, String slackId) {
        return new User(username, "encodedPw", email, UserRole.USER, "COMPANY", 1L, slackId);
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
        assertThat(found.getAffiliationType()).isEqualTo("COMPANY");
        assertThat(found.getAffiliationId()).isEqualTo(1L);
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
        master.setId(99L);
        UserDetailsImpl userDetails = new UserDetailsImpl(master);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        // when
        saved.update("new@test.com", "newEncodedPw", "new-slack", UserRole.MASTER, "HUB", 2000L);
        userRepository.saveAndFlush(saved);

        // then
        assertThat(saved.getUpdatedBy()).isEqualTo(99L);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    // ===== 논리적 삭제 =====

    private void loginAs(Long userId) {
        User loginUser = user("login" + userId, "login" + userId + "@test.com", "slack-login" + userId);
        loginUser.setId(userId);
        UserDetailsImpl userDetails = new UserDetailsImpl(loginUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    // 삭제된 행은 @SQLRestriction 때문에 JPA 로 조회되지 않으므로 네이티브 쿼리로 직접 조회
    private Object[] auditColumns(Long id) {
        return (Object[]) entityManager.createNativeQuery(
                        "SELECT updated_at, updated_by, deleted_at, deleted_by FROM users WHERE id = ?")
                .setParameter(1, id)
                .getSingleResult();
    }

    @Test
    @DisplayName("논리적 삭제 시 deleted_at, deleted_by 만 기록되고 updated_at, updated_by 는 그대로")
    void softDelete_doesNotChangeUpdatedColumns() {
        // given: id 50 회원이 로그인한 상태에서 저장 → updated_by = 50
        loginAs(50L);
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));
        Object[] before = auditColumns(saved.getId());

        // when: id 99 회원이 로그인한 상태에서 삭제
        loginAs(99L);
        int deleted = userRepository.softDeleteById(saved.getId(), 99L, LocalDateTime.now());

        // then
        Object[] after = auditColumns(saved.getId());
        assertThat(deleted).isEqualTo(1);
        assertThat(after[0]).isEqualTo(before[0]);                    // updated_at 그대로
        assertThat(((Number) after[1]).longValue()).isEqualTo(50L);   // updated_by 그대로
        assertThat(after[2]).isNotNull();                             // deleted_at 기록
        assertThat(((Number) after[3]).longValue()).isEqualTo(99L);   // deleted_by 기록
    }

    @Test
    @DisplayName("논리적 삭제된 회원은 조회, 중복 확인에서 제외")
    void softDelete_excludedFromQueries() {
        // given
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));

        // when
        userRepository.softDeleteById(saved.getId(), 1L, LocalDateTime.now());

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
        userRepository.softDeleteById(saved.getId(), 1L, LocalDateTime.now());

        // when & then
        assertThat(userRepository.softDeleteById(saved.getId(), 1L, LocalDateTime.now())).isZero();
        assertThat(userRepository.softDeleteById(999L, 1L, LocalDateTime.now())).isZero();
    }

    @Test
    @DisplayName("삭제된 회원의 username 은 DB unique 제약 때문에 재사용 불가 (409 DUPLICATE_RESOURCE 로 처리)")
    void softDelete_valuesStillOccupied() {
        // given
        User saved = userRepository.saveAndFlush(user("tester", "tester@test.com", "slack-1"));
        userRepository.softDeleteById(saved.getId(), 1L, LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> userRepository.saveAndFlush(user("tester", "other@test.com", "slack-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
}
