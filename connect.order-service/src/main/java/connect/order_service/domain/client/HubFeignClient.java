package connect.order_service.domain.client;

import connect.order_service.domain.client.Api.HubFeignApi;
import connect.order_service.domain.client.clientInterface.HubClient;
import connect.order_service.domain.client.dto.request.StockAdjustRequest;
import connect.order_service.domain.client.dto.request.StockDecreaseRequest;
import connect.order_service.domain.client.dto.request.StockRestoreRequest;
import connect.order_service.domain.client.dto.response.HubInfo;
import connect.order_service.domain.client.dto.response.StockResult;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubFeignClient implements HubClient {
    private final HubFeignApi hubFeignApi;
    private final ObjectMapper objectMapper;

    @Override
    public HubInfo getHub(UUID hubId) {
        return hubFeignApi.getHub(hubId).getData();
    }

    @Override
    public StockResult decreaseStock(UUID orderId, UUID hubId, UUID productId, int quantity) {
        try {
            hubFeignApi.decreaseStock(new StockDecreaseRequest(orderId, hubId, productId, quantity));
            return StockResult.SUCCESS;
        } catch (FeignException.Conflict | FeignException.NotFound e) {
            return toRejection(e);
        }
    }

    @Override
    public void restoreStock(UUID orderId, UUID hubId, UUID productId) {
        try {
            hubFeignApi.restoreStock(new StockRestoreRequest(orderId, hubId, productId));
        } catch (FeignException.Conflict e) {
            String code = extractCode(e);
            if (!"DECREASE_HISTORY_NOT_FOUND".equals(code) && !"STOCK_ALREADY_RESTORED".equals(code)) {
                throw e;
            }
        }
    }

    @Override
    public StockResult adjustStock(UUID adjustmentId, UUID orderId, UUID hubId, UUID productId, int quantityDelta) {
        try {
            hubFeignApi.adjustStock(new StockAdjustRequest(adjustmentId, orderId, hubId, productId, quantityDelta));
            return StockResult.SUCCESS;
        } catch (FeignException.Conflict | FeignException.NotFound e) {
            return toRejection(e);
        }
    }

    private StockResult toRejection(FeignException e) {
        String code = extractCode(e);
        if (code == null) {
            return StockResult.OUT_OF_STOCK;
        }
        return switch (code) {
            case "INSUFFICIENT_STOCK" -> StockResult.OUT_OF_STOCK;
            case "STOCK_DELETED" -> StockResult.DISCONTINUED;
            case "STOCK_NOT_FOUND" -> StockResult.NOT_READY;
            default -> throw e;
        };
    }

    private String extractCode(FeignException e) {
        try {
            JsonNode code = objectMapper.readTree(e.contentUTF8()).get("code");
            return code == null || code.isNull() ? null : code.asString();
        } catch (Exception ex) {
            return null;
        }
    }
}
