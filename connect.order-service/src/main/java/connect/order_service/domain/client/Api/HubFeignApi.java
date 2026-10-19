package connect.order_service.domain.client.Api;

import connect.order_service.domain.client.dto.request.StockAdjustRequest;
import connect.order_service.domain.client.dto.request.StockDecreaseRequest;
import connect.order_service.domain.client.dto.request.StockRestoreRequest;
import connect.order_service.domain.client.dto.response.HubInfo;
import connect.order_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "hub-service")
public interface HubFeignApi {

    @GetMapping("/hubs/{hubId}")
    ApiResponse<HubInfo> getHub(@PathVariable("hubId") UUID hubId);

    @PostMapping("/internal/hubs/stocks/decrease")
    void decreaseStock(@RequestBody StockDecreaseRequest request);

    @PostMapping("/internal/hubs/stocks/restore")
    void restoreStock(@RequestBody StockRestoreRequest request);

    @PostMapping("/internal/hubs/stocks/adjust")
    void adjustStock(@RequestBody StockAdjustRequest request);
}
