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
 * 복원은 기존 DECREASE 이력을 바꾸지 않고 별도의 RESTORE 이력을 추가한다.
 *
 * <p>orderId는 Order의 논리 참조다. 주문당 상품 1개 계약이므로 UNIQUE(order_id, type)을 둔다.
 * 그래서 BaseEntity를 상속하지 않고 createdAt만 JPA Auditing으로 채운다.
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "p_stock_histories",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_histories_order_type", columnNames = {"order_id", "type"}),
        check = {
                @CheckConstraint(name = "ck_stock_histories_quantity_positive", constraint = "quantity > 0"),
                @CheckConstraint(name = "ck_stock_histories_type", constraint = "type in ('DECREASE', 'RESTORE')")
        })
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

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private StockHistoryType type;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private StockHistory(UUID orderId, Stock stock, int quantity, StockHistoryType type) {
        this.orderId = orderId;
        this.stock = stock;
        this.quantity = quantity;
        this.type = type;
    }

    public static StockHistory decrease(UUID orderId, Stock stock, int quantity) {
        return new StockHistory(orderId, stock, quantity, StockHistoryType.DECREASE);
    }

    public static StockHistory restore(UUID orderId, Stock stock, int quantity) {
        return new StockHistory(orderId, stock, quantity, StockHistoryType.RESTORE);
    }
}
