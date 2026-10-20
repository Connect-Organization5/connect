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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
        // 상품 단위 조회(일괄 삭제, 상품별 목록)용. 위 UNIQUE는 선두 컬럼이 hub_id라 product_id 단독 조회에 맞지 않는다.
        indexes = @Index(name = "idx_stocks_product_id", columnList = "product_id"),
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

    // 차감: 수량을 줄인다. 호출 측이 부족 여부를 먼저 확인하며, 여기서는 음수 재고를 막는 방어만 한다.
    public void decrease(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다.");
        }
        if (this.quantity < amount) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.quantity -= amount;
    }

    // 논리 삭제: 수량은 그대로 두고 삭제 시각·삭제자만 기록한다. 이미 삭제된 재고는 바꾸지 않고 false를 반환한다.
    public boolean delete(UUID userId, LocalDateTime deletedAt) {
        if (getDeletedAt() != null) {
            return false;
        }
        markDeleted(userId, deletedAt);
        return true;
    }

    // 입고: 수량을 늘린다. int 범위를 넘으면 ArithmeticException (호출 측에서 도메인 오류로 변환)
    public void increase(int amount) {
        this.quantity = Math.addExact(this.quantity, amount);
    }
}
