package connect.delivery_service.domain.entity;

import connect.delivery_service.global.entity.BaseEntity;
import jakarta.persistence.*;
import connect.delivery_service.global.exception.BaseException;
import connect.delivery_service.global.exception.ErrorCode;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity(name = "p_deliveries")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
public class Delivery extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "orderer_id", nullable = false, updatable = false)
    private UUID ordererId;

    private UUID deliveryUserId;

    @Column(nullable = false)
    private UUID departureHubId;

    @Column(nullable = false)
    private UUID arrivalHubId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus deliveryStatus;

    @Column(nullable = false, length = 200)
    private String deliveryAddress;

    @Column(nullable = false, length = 50)
    private String receiver;

    public static Delivery createDelivery(UUID orderId, UUID ordererId, UUID departureHubId, UUID arrivalHubId,
                                  String receiver, String deliveryAddress) {
        return Delivery.builder()
                .orderId(orderId)
                .ordererId(ordererId)
                .deliveryUserId(null)
                .deliveryStatus(DeliveryStatus.HUB_WAITING)
                .departureHubId(departureHubId)
                .arrivalHubId(arrivalHubId)
                .receiver(receiver)
                .deliveryAddress(deliveryAddress)
                .build();
    }

    // 배송 상태 변경
    public void startHubMoving(){
        validateDeliveryStatus(DeliveryStatus.HUB_WAITING);
        validateAssigned();
        this.deliveryStatus = DeliveryStatus.HUB_MOVING;
    }

    public void arrivedHub(){
        validateDeliveryStatus(DeliveryStatus.HUB_MOVING);
        this.deliveryStatus = DeliveryStatus.DEST_HUB_ARRIVED;
    }

    public void startDelivery(){
        validateDeliveryStatus(DeliveryStatus.DEST_HUB_ARRIVED);
        this.deliveryStatus = DeliveryStatus.OUT_FOR_DELIVERY;
    }

    public void complete(){
        validateDeliveryStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        this.deliveryStatus = DeliveryStatus.DELIVERED;
    }

    public void cancel(){
        validateDeliveryStatus(DeliveryStatus.HUB_WAITING);
        this.deliveryStatus = DeliveryStatus.CANCELED;
    }

    // 담당자 배정
    public void assignDeliveryUserId(UUID deliveryUserId) {
        validateDeliveryStatus(DeliveryStatus.HUB_WAITING);
        if (this.deliveryUserId != null){
            throw new BaseException(ErrorCode.DELIVERY_USER_ALREADY_ASSIGNED);
        }
        this.deliveryUserId = deliveryUserId;
    }

    // 담당자 변경
    public void changeDeliveryUserId(UUID deliveryUserId) {
        validateDeliveryStatus(DeliveryStatus.HUB_WAITING);
        this.deliveryUserId = deliveryUserId;
    }

    // 수령인 변경
    public void changeReceiver(String receiver) {
        validateDeliveryStatus(DeliveryStatus.HUB_WAITING);
        this.receiver = receiver;
    }

    // 담당자 확인
    public boolean isManagedBy(UUID deliveryUserId) {
        return this.deliveryUserId != null && this.deliveryUserId.equals(deliveryUserId);
    }

    // 본인 확인
    public boolean isOrderedBy(UUID userId) {
        return this.ordererId != null && this.ordererId.equals(userId);
    }


    // 삭제
    public void delete(UUID userId) {
        if (this.deliveryStatus != DeliveryStatus.DELIVERED && this.deliveryStatus != DeliveryStatus.CANCELED) {
            throw new BaseException(ErrorCode.INVALID_DELIVERY_STATUS);
        }
        softDelete(userId);
    }

    private void validateDeliveryStatus(DeliveryStatus expectedStatus) {
        if (this.deliveryStatus != expectedStatus){
            throw new BaseException(ErrorCode.INVALID_DELIVERY_STATUS);
        }
    }

    private void validateAssigned() {
        if (this.deliveryUserId == null) {
            throw new BaseException(ErrorCode.DELIVERY_USER_NOT_ASSIGNED);
        }
    }
}
