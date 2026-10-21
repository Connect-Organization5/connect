package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.response.HubInfo;
import connect.order_service.domain.client.dto.response.StockResult;

import java.util.UUID;

public interface HubClient {
    HubInfo getHub(UUID hubId);

    StockResult decreaseStock(UUID orderId, UUID hubId, UUID productId, int quantity);

    void restoreStock(UUID orderId, UUID hubId, UUID productId);

    StockResult adjustStock(UUID adjustmentId, UUID orderId, UUID hubId, UUID productId, int quantityDelta);
}
