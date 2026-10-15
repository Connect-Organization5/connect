package connect.delivery_service.domain.entity;

import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.global.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity(name = "p_deliveries")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Delivery extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID deliveryUserId;

    @Column(nullable = false)
    private UUID departureHubId;

    @Column(nullable = false)
    private UUID arrivalHubId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus deliveryStatus;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false)
    private String receiver;

    public static Delivery createDelivery(UUID orderId, UUID deliveryUserId, UUID departureHubId, UUID arrivalHubId,
                                  String receiver, String deliveryAddress) {
        return Delivery.builder()
                .orderId(orderId)
                .deliveryUserId(deliveryUserId)
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

    // 담당자 변경
    public void changeDeliveryUserId(UUID deliveryUserId) {
        if (this.deliveryStatus != DeliveryStatus.HUB_WAITING) {
            throw new IllegalStateException();
        }
        this.deliveryUserId = deliveryUserId;
    }

    // 수령인 변경
    public void changeReceiver(String receiver) {
        if (this.deliveryStatus != DeliveryStatus.HUB_WAITING) {
            throw new IllegalStateException();
        }
        this.receiver = receiver;
    }

    // 담당자 확인
    public boolean isManagedBy(UUID deliveryUserId) {
        return this.deliveryUserId != null && this.deliveryUserId.equals(deliveryUserId);
    }

    private void validateDeliveryStatus(DeliveryStatus expectedStatus) {
        if (this.deliveryStatus != expectedStatus){
            throw new IllegalStateException();
        }
    }
}
