package connect.delivery_service.client;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class HubRouteResponse {
    private BigDecimal estimatedDistance;
    private Integer estimatedDuration;
}
