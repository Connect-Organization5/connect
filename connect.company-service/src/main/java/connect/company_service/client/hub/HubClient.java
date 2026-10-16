package connect.company_service.client.hub;

import connect.company_service.client.hub.dto.HubResponse;
import connect.company_service.client.hub.dto.StockDeleteRequest;
import connect.company_service.client.hub.dto.StockDeleteResponse;
import connect.company_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

// Eureka에 등록된 hub-service를 호출 (Hub DB 직접 접근 금지)
@FeignClient(name = "hub-service")
public interface HubClient {

    // 존재하지 않는 Hub면 Hub Service가 404 → FeignException.NotFound 발생
    @GetMapping("/hubs/{hubId}")
    ApiResponse<HubResponse> getHub(@PathVariable("hubId") UUID hubId);

    // productIds에 해당하는 활성 Stock을 soft delete. 대상이 없거나 이미 삭제된 Stock이어도 200 (deletedCount: 0)
    @PostMapping("/internal/hubs/stocks/delete")
    ApiResponse<StockDeleteResponse> deleteStocks(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody StockDeleteRequest request
    );
}
