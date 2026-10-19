package connect.user_service.domain.user.entity;

import connect.user_service.global.entity.TimeStamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Setter
@NoArgsConstructor
// 마지막 MASTER 확인용 잠금 조회가 이 인덱스로 해당 소속의 행만 잠그도록 함 (인덱스가 없으면 훑은 행을 모두 잠금)
@Table(name = "users", indexes = @Index(name = "idx_users_affiliation_role",
        columnList = "affiliation_type, affiliation_id, role"))
@SQLRestriction("deleted_at IS NULL") // 논리적으로 삭제된 회원은 모든 조회에서 제외
public class User extends TimeStamp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private UserRole role;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private AffiliationType affiliationType;

    @Column(nullable = false)
    private Long affiliationId;

    @Column(nullable = false, unique = true)
    private String slackId;

    public User(String username, String password, String email, UserRole role, AffiliationType affiliationType, Long affiliationId, String slackId) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
        this.affiliationType = affiliationType;
        this.affiliationId = affiliationId;
        this.slackId = slackId;
    }

    // 회원 정보 수정 (username, 소속은 변경 불가, updatedAt/By 는 JPA Auditing 이 기록)
    public void update(String email, String encodedPassword, String slackId, UserRole role) {
        this.email = email;
        this.password = encodedPassword;
        this.slackId = slackId;
        this.role = role;
    }
}