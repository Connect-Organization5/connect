package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 같은 소속의 모든 회원 조회 (삭제된 회원은 @SQLRestriction 으로 제외)
    // 정렬: MASTER 먼저 → createdAt 내림차순 (값이 없는 기존 회원은 마지막) → id 내림차순 (같은 시각일 때 순서 고정)
    @Query(value = "SELECT u FROM User u " +
            "WHERE u.affiliationType = :affiliationType AND u.affiliationId = :affiliationId " +
            "ORDER BY CASE WHEN u.role = :firstRole THEN 0 ELSE 1 END, " +
            "u.createdAt DESC NULLS LAST, u.id DESC",
            countQuery = "SELECT COUNT(u) FROM User u " +
                    "WHERE u.affiliationType = :affiliationType AND u.affiliationId = :affiliationId")
    Page<User> findAllByAffiliation(@Param("affiliationType") AffiliationType affiliationType,
                                    @Param("affiliationId") Long affiliationId,
                                    @Param("firstRole") UserRole firstRole,
                                    Pageable pageable);

    // 같은 소속의 회원 수 (삭제된 회원 포함)
    // 네이티브 쿼리는 @SQLRestriction 이 적용되지 않으므로 삭제된 회원도 셈 (소속 카운터의 시작값)
    @Query(value = "SELECT COUNT(*) FROM users WHERE affiliation_type = :affiliationType AND affiliation_id = :affiliationId",
            nativeQuery = true)
    long countAllIncludingDeleted(@Param("affiliationType") String affiliationType,
                                  @Param("affiliationId") Long affiliationId);

    // 논리적 삭제: deleted_at, deleted_by 만 변경 (JPA Auditing 을 거치지 않아 updated_at, updated_by 는 그대로)
    // 이미 삭제된 회원이면 0 반환
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.deletedAt = :deletedAt, u.deletedBy = :deletedBy " +
            "WHERE u.id = :id AND u.deletedAt IS NULL")
    int softDeleteById(@Param("id") Long id,
                       @Param("deletedBy") Long deletedBy,
                       @Param("deletedAt") LocalDateTime deletedAt);
}