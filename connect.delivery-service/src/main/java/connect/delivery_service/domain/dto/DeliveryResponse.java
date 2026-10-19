package connect.delivery_service.domain.dto;

import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryResponse {
    private UUID id;
    private UUID orderId;
    private UUID deliveryUserId;
    private UUID departureHubId;
    private UUID arrivalHubId;
    private DeliveryStatus deliveryStatus;
    private String deliveryAddress;
    private String receiver;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getDeliveryUserId(),
                delivery.getDepartureHubId(),
                delivery.getArrivalHubId(),
                delivery.getDeliveryStatus(),
                delivery.getDeliveryAddress(),
                delivery.getReceiver(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}
