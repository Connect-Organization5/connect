package connect.hub_service.stock.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 입고(수량 추가) 기록. 생성 이후 수정·삭제하지 않는 append-only 데이터다.
 *
 * <p>inboundId는 요청하는 쪽이 입고 한 건마다 만드는 멱등 키다. 같은 입고를 재시도할 때는 같은 값을 보내며,
 * UNIQUE(inbound_id)로 같은 입고가 두 번 반영되지 않게 한다.
 * 주문용 이력(p_stock_histories)은 order_id가 필수이고 주문 유형만 허용하는 CHECK가 있어 별도 테이블로 둔다.
 * BaseEntity를 상속하지 않고 createdAt만 JPA Auditing으로 채운다.
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "p_stock_inbounds",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_inbounds_inbound_id", columnNames = "inbound_id"),
        // 재고별 입고 이력을 최신순으로 조회한다.
        indexes = @Index(name = "idx_stock_inbounds_stock_created", columnList = "stock_id, created_at"),
        check = @CheckConstraint(name = "ck_stock_inbounds_quantity_positive", constraint = "quantity > 0"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockInbound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "inbound_id", nullable = false, updatable = false)
    private UUID inboundId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    // 입고를 요청한 사용자(Gateway가 전달한 X-User-Id)
    @Column(name = "inbound_by", nullable = false, updatable = false)
    private UUID inboundBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private StockInbound(UUID inboundId, Stock stock, int quantity, UUID inboundBy) {
        this.inboundId = inboundId;
        this.stock = stock;
        this.quantity = quantity;
        this.inboundBy = inboundBy;
    }

    public static StockInbound of(UUID inboundId, Stock stock, int quantity, UUID inboundBy) {
        return new StockInbound(inboundId, stock, quantity, inboundBy);
    }
}
