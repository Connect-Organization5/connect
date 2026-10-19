package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsBySlackId(String slackId);
    // 회원 정보 수정 시 자기 자신을 제외한 중복 확인
    boolean existsByEmailAndIdNot(String email, UUID id);
    boolean existsBySlackIdAndIdNot(String slackId, UUID id);

    // 같은 소속의 특정 권한 회원을 SELECT ... FOR UPDATE 로 잠그고 조회 (삭제된 회원은 @SQLRestriction 으로 제외)
    // 마지막 MASTER 삭제/강등 방지에 사용: 같은 소속의 요청은 먼저 잠근 트랜잭션이 끝날 때까지 기다린 뒤 최신 값으로 다시 셈
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u " +
            "WHERE u.affiliationType = :affiliationType AND u.affiliationId = :affiliationId AND u.role = :role")
    List<User> findAllByAffiliationAndRoleForUpdate(@Param("affiliationType") AffiliationType affiliationType,
                                                   @Param("affiliationId") UUID affiliationId,
                                                   @Param("role") UserRole role);

    // 같은 소속의 모든 회원 조회 (삭제된 회원은 @SQLRestriction 으로 제외)
    // 정렬: MASTER 먼저 → createdAt 내림차순 (값이 없는 기존 회원은 마지막) → id 내림차순 (같은 시각일 때 순서 고정)
    @Query(value = "SELECT u FROM User u " +
            "WHERE u.affiliationType = :affiliationType AND u.affiliationId = :affiliationId " +
            "ORDER BY CASE WHEN u.role = :firstRole THEN 0 ELSE 1 END, " +
            "u.createdAt DESC NULLS LAST, u.id DESC",
            countQuery = "SELECT COUNT(u) FROM User u " +
                    "WHERE u.affiliationType = :affiliationType AND u.affiliationId = :affiliationId")
    Page<User> findAllByAffiliation(@Param("affiliationType") AffiliationType affiliationType,
                                    @Param("affiliationId") UUID affiliationId,
                                    @Param("firstRole") UserRole firstRole,
                                    Pageable pageable);

    // 논리적 삭제: deleted_at, deleted_by 만 변경 (JPA Auditing 을 거치지 않아 updated_at, updated_by 는 그대로)
    // 이미 삭제된 회원이면 0 반환
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.deletedAt = :deletedAt, u.deletedBy = :deletedBy " +
            "WHERE u.id = :id AND u.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("deletedBy") UUID deletedBy,
                       @Param("deletedAt") LocalDateTime deletedAt);
}