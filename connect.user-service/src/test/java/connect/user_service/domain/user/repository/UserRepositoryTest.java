package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.global.config.JpaAuditingConfig;
import connect.user_service.global.security.UserDetailsImpl;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
}
