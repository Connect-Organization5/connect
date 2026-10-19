package connect.delivery_service.domain.dto;

import connect.delivery_service.domain.entity.DeliveryRoute;
import connect.delivery_service.domain.entity.DeliveryRouteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryRouteResponse {
    private UUID id;
    private UUID deliveryId;
    private UUID deliveryUserId;
    private UUID departureHubId;
    private UUID arrivalHubId;
    private DeliveryRouteStatus deliveryRouteStatus;
    private BigDecimal estimatedDistance;
    private Integer estimatedDuration;
    private BigDecimal actualDistance;
    private Integer actualDuration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DeliveryRouteResponse from(DeliveryRoute deliveryRoute) {
        return new DeliveryRouteResponse(
                deliveryRoute.getId(),
                deliveryRoute.getDeliveryId(),
                deliveryRoute.getDeliveryUserId(),
                deliveryRoute.getDepartureHubId(),
                deliveryRoute.getArrivalHubId(),
                deliveryRoute.getDeliveryRouteStatus(),
                deliveryRoute.getEstimatedDistance(),
                deliveryRoute.getEstimatedDuration(),
                deliveryRoute.getActualDistance(),
                deliveryRoute.getActualDuration(),
                deliveryRoute.getCreatedAt(),
                deliveryRoute.getUpdatedAt()
        );
    }
}
