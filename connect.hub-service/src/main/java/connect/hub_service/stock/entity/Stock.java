package connect.hub_service.stock.entity;

import connect.hub_service.global.entity.BaseEntity;
import connect.hub_service.hub.entity.Hub;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.UUID;

/**
 * 허브·상품별 현재 재고. productId는 Company 상품의 논리 참조다(물리 FK 없음).
 *
 * <p>논리 삭제(deleted_at)된 재고도 UNIQUE(hub_id, product_id)에 포함된다. 삭제된 재고를 복원 등에서 조회해야 할 수 있어
 * 엔티티 단위 @SQLRestriction은 사용하지 않고, 일반 조회는 Repository에서 deletedAt is null을 명시한다.
 */
@Getter
@Entity
@Table(name = "p_stocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_stocks_hub_product", columnNames = {"hub_id", "product_id"}),
        check = @CheckConstraint(name = "ck_stocks_quantity_non_negative", constraint = "quantity >= 0"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hub_id", nullable = false)
    private Hub hub;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    public Stock(Hub hub, UUID productId, Integer quantity) {
        this.hub = hub;
        this.productId = productId;
        this.quantity = quantity;
    }

    // 입고: 수량을 늘린다. int 범위를 넘으면 ArithmeticException (호출 측에서 도메인 오류로 변환)
    public void increase(int amount) {
        this.quantity = Math.addExact(this.quantity, amount);
    }
}
