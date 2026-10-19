package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsBySlackId(String slackId);
    // 회원 정보 수정 시 자기 자신을 제외한 중복 확인
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsBySlackIdAndIdNot(String slackId, Long id);
}