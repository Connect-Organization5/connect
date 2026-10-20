package connect.delivery_service.domain.entity;

import connect.delivery_service.global.entity.BaseEntity;
import jakarta.persistence.*;
import connect.delivery_service.global.exception.BaseException;
import connect.delivery_service.global.exception.ErrorCode;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity(name = "p_delivery_routes")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
public class DeliveryRoute extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID deliveryId;

    private UUID deliveryUserId;

    @Column(nullable = false)
    private UUID departureHubId;

    @Column(nullable = false)
    private UUID arrivalHubId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryRouteStatus deliveryRouteStatus;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedDistance;

    @Column(nullable = false)
    private Integer estimatedDuration;

    @Column(precision = 10, scale = 2)
    private BigDecimal actualDistance;

    private Integer actualDuration;

    public static DeliveryRoute createDeliveryRoute(UUID deliveryId, UUID deliveryUserId, UUID departureHubId, UUID arrivalHubId,
                                               BigDecimal estimatedDistance, Integer estimatedDuration) {
        return DeliveryRoute.builder()
                .deliveryId(deliveryId)
                .deliveryUserId(deliveryUserId)
                .deliveryRouteStatus(DeliveryRouteStatus.WAITING)
                .departureHubId(departureHubId)
                .arrivalHubId(arrivalHubId)
                .estimatedDistance(estimatedDistance)
                .estimatedDuration(estimatedDuration)
                .actualDistance(null)
                .actualDuration(null)
                .build();
    }

    public void startMoving(){
        validateDeliveryRouteStatus(DeliveryRouteStatus.WAITING);
        this.deliveryRouteStatus = DeliveryRouteStatus.MOVING;
    }

    public void complete(BigDecimal actualDistance, Integer actualDuration) {
        validateDeliveryRouteStatus(DeliveryRouteStatus.MOVING);
        this.deliveryRouteStatus = DeliveryRouteStatus.ARRIVED;
        this.actualDistance = actualDistance;
        this.actualDuration = actualDuration;
    }

    public void cancel(){
        validateDeliveryRouteStatus(DeliveryRouteStatus.WAITING);
        this.deliveryRouteStatus = DeliveryRouteStatus.CANCELED;
    }

    // 담당자 배정
    public void assignDeliveryUserId(UUID deliveryUserId) {
        validateDeliveryRouteStatus(DeliveryRouteStatus.WAITING);
        if (this.deliveryUserId != null){
            throw new BaseException(ErrorCode.DELIVERY_USER_ALREADY_ASSIGNED);
        }
        this.deliveryUserId = deliveryUserId;
    }

    // 담당자 변경
    public void changeDeliveryUserId(UUID deliveryUserId) {
        validateDeliveryRouteStatus(DeliveryRouteStatus.WAITING);
        this.deliveryUserId = deliveryUserId;
    }

    public void delete(UUID userId) {
        softDelete(userId);
    }


    private void validateDeliveryRouteStatus(DeliveryRouteStatus expectedStatus) {
        if (this.deliveryRouteStatus != expectedStatus){
            throw new BaseException(ErrorCode.INVALID_DELIVERY_STATUS);
        }
    }
}
