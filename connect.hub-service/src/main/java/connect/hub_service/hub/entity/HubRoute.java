package connect.hub_service.hub.entity;

import connect.hub_service.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 허브 간 P2P 직접 이동 정보. 초기 적재 후 조회 전용 기준 데이터다.
 * distance(km)와 duration(분)은 허브 대표 좌표 기반 추정값이며 실제 도로 측정값이 아니다.
 */
@Getter
@Entity
@Table(name = "p_hub_routes", uniqueConstraints = @UniqueConstraint(
        name = "uk_hub_routes_departure_arrival", columnNames = {"departure_hub_id", "arrival_hub_id"}))
@SQLRestriction("deleted_at is null")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubRoute extends BaseEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_hub_id", nullable = false)
    private Hub departureHub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arrival_hub_id", nullable = false)
    private Hub arrivalHub;

    // 예상(추정) 이동 거리(km)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal distance;

    // 예상(추정) 소요시간(분)
    @Column(nullable = false)
    private Integer duration;

    public HubRoute(UUID id, Hub departureHub, Hub arrivalHub, BigDecimal distance, Integer duration) {
        this.id = id;
        this.departureHub = departureHub;
        this.arrivalHub = arrivalHub;
        this.distance = distance;
        this.duration = duration;
    }
}
