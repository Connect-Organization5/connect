package connect.hub_service.hub.entity;

import connect.hub_service.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

// 초기 적재 후 조회 전용 기준 데이터. ID는 적재 시 고정 UUID를 지정한다.
@Getter
@Entity
@Table(name = "p_hubs")
@SQLRestriction("deleted_at is null")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hub extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    public Hub(UUID id, String name, String address) {
        this.id = id;
        this.name = name;
        this.address = address;
    }
}
