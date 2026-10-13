package connect.order_service.domain.client.clientInterface;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public interface HubClient {
    boolean decreaseStock(UUID hubId, UUID productId, int quantity, UUID orderId);

    void increaseStock(UUID hubId, UUID productId, int quantity, UUID orderId);
}
