package connect.delivery_service.domain.entity;

import connect.delivery_service.global.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity(name = "p_delivery_routes")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DeliveryRoute extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID deliveryId;

    @Column(nullable = false)
    private UUID deliveryUserId;

    @Column(nullable = false)
    private UUID departureHubId;

    @Column(nullable = false)
    private UUID arrivalHubId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryRouteStatus deliveryRouteStatus;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal EstimatedDistance;

    @Column(nullable = false)
    private Integer estimatedDuration;

    @Column(precision = 10, scale = 2)
    private BigDecimal ActualDistance;

    private Integer ActualDuration;

    public static DeliveryRoute createDeliveryRoute(UUID deliveryId, UUID deliveryUserId, UUID departureHubId, UUID arrivalHubId,
                                               BigDecimal EstimatedDistance, Integer estimatedDuration, BigDecimal ActualDistance, Integer ActualDuration) {
        return DeliveryRoute.builder()
                .deliveryId(deliveryId)
                .deliveryUserId(deliveryUserId)
                .deliveryRouteStatus(DeliveryRouteStatus.WAITING)
                .departureHubId(departureHubId)
                .arrivalHubId(arrivalHubId)
                .EstimatedDistance(EstimatedDistance)
                .estimatedDuration(estimatedDuration)
                .estimatedDuration(estimatedDuration)
                .ActualDistance(ActualDistance)
                .ActualDuration(ActualDuration)
                .build();
    }

    // 배송 상태 변경
    public void startMoving(){
        validateDeliveryRouteStatus(DeliveryRouteStatus.WAITING);
        this.deliveryRouteStatus = DeliveryRouteStatus.MOVING;
    }

    public void complete(){
        validateDeliveryRouteStatus(DeliveryRouteStatus.MOVING);
        this.deliveryRouteStatus = DeliveryRouteStatus.ARRIVED;
    }

    public void cancel(){
        validateDeliveryRouteStatus(DeliveryRouteStatus.WAITING);
        this.deliveryRouteStatus = DeliveryRouteStatus.CANCELED;
    }

    private void validateDeliveryRouteStatus(DeliveryRouteStatus expectedStatus) {
        if (this.deliveryRouteStatus != expectedStatus){
            throw new IllegalStateException();
        }
    }
}
