package connect.hub_service.stock.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 주문 기반 재고 변경 이력. 생성 이후 수정·삭제하지 않는 append-only 데이터다.
 * 복원은 기존 DECREASE 이력을 바꾸지 않고 별도의 RESTORE 이력을 추가하고, 주문 수량 수정은 ADJUST 이력을 추가한다.
 *
 * <p>주문이 현재 점유한 수량은 별도 테이블 없이 이력을 합산해 구한다: DECREASE.quantity + 모든 ADJUST.quantityDelta
 * (RESTORE가 있으면 0).
 *
 * <p>멱등 키는 공통 컬럼 idempotency_key에 저장한다. DECREASE·RESTORE는 orderId, ADJUST는 adjustmentId다.
 * UNIQUE(type, idempotency_key) 하나로 주문당 DECREASE·RESTORE 1건과 조정 작업 1건을 보장하며,
 * 같은 주문의 ADJUST는 키가 달라 여러 건 저장된다. 유형별 필드 조합은 CHECK로 제한한다.
 * orderId는 Order의 논리 참조다. BaseEntity를 상속하지 않고 createdAt만 JPA Auditing으로 채운다.
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "p_stock_histories",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_history_type_idempotency",
                columnNames = {"type", "idempotency_key"}),
        check = @CheckConstraint(name = "ck_stock_histories_shape", constraint =
                // CHECK는 결과가 NULL이면 통과하므로 필수 필드는 is not null을 명시해야 한다.
                "(type in ('DECREASE', 'RESTORE') and idempotency_key = order_id"
                        + " and quantity is not null and quantity > 0 and quantity_delta is null)"
                        + " or (type = 'ADJUST' and quantity is null"
                        + " and quantity_delta is not null and quantity_delta <> 0)"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private StockHistoryType type;

    // 멱등 키. DECREASE·RESTORE는 orderId, ADJUST는 조정 작업 ID(adjustmentId)다.
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    // DECREASE·RESTORE의 수량(양수). ADJUST에서는 null이다.
    @Column(name = "quantity", updatable = false)
    private Integer quantity;

    // ADJUST의 증감량(0이 아닌 정수). 양수는 주문 수량 증가(재고 차감), 음수는 감소(재고 증가). 다른 유형에서는 null이다.
    @Column(name = "quantity_delta", updatable = false)
    private Integer quantityDelta;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private StockHistory(UUID orderId, Stock stock, StockHistoryType type, UUID idempotencyKey,
                         Integer quantity, Integer quantityDelta) {
        this.orderId = orderId;
        this.stock = stock;
        this.type = type;
        this.idempotencyKey = idempotencyKey;
        this.quantity = quantity;
        this.quantityDelta = quantityDelta;
    }

    public static StockHistory decrease(UUID orderId, Stock stock, int quantity) {
        return new StockHistory(orderId, stock, StockHistoryType.DECREASE, orderId, quantity, null);
    }

    public static StockHistory adjust(UUID orderId, Stock stock, UUID adjustmentId, int quantityDelta) {
        return new StockHistory(orderId, stock, StockHistoryType.ADJUST, adjustmentId, null, quantityDelta);
    }

    public static StockHistory restore(UUID orderId, Stock stock, int quantity) {
        return new StockHistory(orderId, stock, StockHistoryType.RESTORE, orderId, quantity, null);
    }

    // API에서 쓰는 조정 작업 ID. ADJUST일 때만 값이 있다.
    public UUID getAdjustmentId() {
        return type == StockHistoryType.ADJUST ? idempotencyKey : null;
    }
}
