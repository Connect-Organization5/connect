package connect.delivery_service.domain.dto;

import connect.delivery_service.domain.entity.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CreateDeliveryRequest {
    private UUID orderId;
    private UUID deliveryUserId;
    private UUID departureHubId;
    private UUID arrivalHubId;
    private String deliveryAddress;
    private String receiver;
}
