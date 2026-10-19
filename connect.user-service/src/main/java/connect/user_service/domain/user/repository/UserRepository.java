package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // 논리적 삭제: deleted_at, deleted_by 만 변경 (JPA Auditing 을 거치지 않아 updated_at, updated_by 는 그대로)
    // 이미 삭제된 회원이면 0 반환
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.deletedAt = :deletedAt, u.deletedBy = :deletedBy " +
            "WHERE u.id = :id AND u.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id,
                       @Param("deletedBy") Long deletedBy,
                       @Param("deletedAt") LocalDateTime deletedAt);
}