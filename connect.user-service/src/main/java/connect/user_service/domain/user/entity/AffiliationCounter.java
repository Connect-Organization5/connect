package connect.user_service.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

// 소속별 가입자 수 (삭제된 회원 포함, 줄어들지 않음)
// 가입 순서를 정하기 위해 행 잠금(PESSIMISTIC_WRITE)을 걸고 1씩 증가시킴
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "affiliation_counters")
public class AffiliationCounter implements Persistable<AffiliationCounterId> {

    @EmbeddedId
    private AffiliationCounterId id;

    @Column(nullable = false)
    private long memberCount;

    // 기본 키를 직접 지정하므로, save 가 merge(SELECT 후 UPDATE) 대신 항상 INSERT 하도록 새 엔티티 여부를 직접 관리
    // (동시에 처음 생성할 때 다른 요청이 만든 카운터를 덮어쓰지 않고 기본 키 충돌로 실패하게 하기 위함)
    @Transient
    private boolean isNew = true;

    public AffiliationCounter(AffiliationType affiliationType, Long affiliationId, long memberCount) {
        this.id = new AffiliationCounterId(affiliationType, affiliationId);
        this.memberCount = memberCount;
    }

    // 가입자 수를 1 늘리고, 늘어난 값(이번 가입자의 순서)을 반환
    public long increase() {
        return ++memberCount;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
