package connect.user_service.domain.client.Api;


import connect.user_service.domain.user.dto.request.StockAdjustRequest;
import connect.user_service.domain.user.dto.request.StockDecreaseRequest;
import connect.user_service.domain.user.dto.request.StockRestoreRequest;
import connect.user_service.domain.user.dto.response.HubInfo;
import connect.user_service.global.dto.ApiResponse;
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
