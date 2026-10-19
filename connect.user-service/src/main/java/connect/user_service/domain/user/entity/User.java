package connect.user_service.domain.user.entity;

import connect.user_service.global.entity.TimeStamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "users")
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
    private String affiliationType;

    @Column(nullable = false)
    private Long affiliationId;

    @Column(nullable = false, unique = true)
    private String slackId;

    public User(String username, String password, String email, UserRole role, String affiliationType, Long affiliationId, String slackId) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
        this.affiliationType = affiliationType;
        this.affiliationId = affiliationId;
        this.slackId = slackId;
    }

    // 회원 정보 수정 (username 은 변경 불가, updatedAt/By 는 JPA Auditing 이 기록)
    public void update(String email, String encodedPassword, String slackId,
                       UserRole role, String affiliationType, Long affiliationId) {
        this.email = email;
        this.password = encodedPassword;
        this.slackId = slackId;
        this.role = role;
        this.affiliationType = affiliationType;
        this.affiliationId = affiliationId;
    }
}