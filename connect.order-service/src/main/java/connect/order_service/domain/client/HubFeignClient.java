package connect.order_service.domain.client;

import connect.order_service.domain.client.Api.HubFeignApi;
import connect.order_service.domain.client.clientInterface.HubClient;
import connect.order_service.domain.client.dto.request.StockAdjustRequest;
import connect.order_service.domain.client.dto.request.StockDecreaseRequest;
import connect.order_service.domain.client.dto.request.StockRestoreRequest;
import connect.order_service.domain.client.dto.response.HubInfo;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubFeignClient implements HubClient {
    private final HubFeignApi hubFeignApi;

    @Override
    public HubInfo getHub(UUID hubId) {
        return hubFeignApi.getHub(hubId).getData();
    }

    @Override
    public boolean decreaseStock(UUID orderId, UUID hubId, UUID productId, int quantity) {
        try {
            hubFeignApi.decreaseStock(new StockDecreaseRequest(orderId, hubId, productId, quantity));
            return true;
        } catch (FeignException.Conflict | FeignException.NotFound e) {
            // 409 재고 부족·삭제된 재고, 404 재고 미등록 → 주문 불가
            return false;
        }
        // 그 외 오류(서버 다운 등)는 그대로 던짐
    }

    @Override
    public void restoreStock(UUID orderId, UUID hubId, UUID productId) {
        hubFeignApi.restoreStock(new StockRestoreRequest(orderId, hubId, productId));
    }

    @Override
    public boolean adjustStock(UUID adjustmentId, UUID orderId, UUID hubId, UUID productId, int quantityDelta) {
        try {
            hubFeignApi.adjustStock(new StockAdjustRequest(adjustmentId, orderId, hubId, productId, quantityDelta));
            return true;
        } catch (FeignException.Conflict e) {
            return false;   // 재고 부족
        }
    }

}
