package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// H2 인메모리 DB 로 실제 저장과 엔티티 제약 조건을 검증
@DataJpaTest
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
}
