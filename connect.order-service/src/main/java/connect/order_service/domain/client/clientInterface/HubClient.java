package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.response.HubInfo;

import java.util.UUID;

public interface HubClient {
    HubInfo getHub(UUID hubId);

    boolean decreaseStock(UUID orderId, UUID hubId, UUID productId, int quantity);

    void restoreStock(UUID orderId, UUID hubId, UUID productId);

    boolean adjustStock(UUID adjustmentId, UUID orderId, UUID hubId, UUID productId, int quantityDelta);
}
