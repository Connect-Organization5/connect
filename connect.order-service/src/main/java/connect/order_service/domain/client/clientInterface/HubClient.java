package connect.order_service.domain.client.clientInterface;

import java.util.UUID;

public interface HubClient {
    boolean decreaseStock(UUID hubId, UUID productId, int quantity, UUID orderId);

    void increaseStock(UUID hubId, UUID productId, int quantity, UUID orderId);
}
