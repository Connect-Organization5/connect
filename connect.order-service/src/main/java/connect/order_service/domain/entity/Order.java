package connect.order_service.domain.entity;

import connect.order_service.global.entity.TimeStamp;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends TimeStamp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID requesterCompanyId;

    @Column(nullable = false)
    private UUID supplierCompanyId;

    @Column(nullable = false)
    private UUID productId;

    private UUID deliveryId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 200)
    private String requestMessage;

    @Column(nullable = false, length = 50)
    private String receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Builder
    private Order(UUID requesterCompanyId, UUID supplierCompanyId, UUID productId,
                  Integer quantity, String requestMessage, String receiver) {
        this.requesterCompanyId = requesterCompanyId;
        this.supplierCompanyId = supplierCompanyId;
        this.productId = productId;
        this.quantity = quantity;
        this.requestMessage = requestMessage;
        this.receiver = receiver;
        this.status = OrderStatus.PENDING;
    }

    public void complete(UUID deliveryId) {
        this.deliveryId = deliveryId;
        this.status = OrderStatus.CREATED;
    }

    public void update(UUID productId, UUID supplierCompanyId, Integer quantity,
                       String requestMessage, String receiver) {
        this.productId = productId;
        this.supplierCompanyId = supplierCompanyId;
        this.quantity = quantity;
        this.requestMessage = requestMessage;
        this.receiver = receiver;
    }

    public void delete(UUID userId) {
        this.status = OrderStatus.CANCELED;
        softDelete(userId);
    }

    public boolean isCanceled() {
        return this.status == OrderStatus.CANCELED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELED;
    }
}
