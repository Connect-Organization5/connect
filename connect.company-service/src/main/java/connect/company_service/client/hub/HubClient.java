package connect.company_service.client.hub;

import connect.company_service.client.hub.dto.HubResponse;
import connect.company_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// Eureka에 등록된 hub-service를 호출 (Hub DB 직접 접근 금지)
@FeignClient(name = "hub-service")
public interface HubClient {

    // 존재하지 않는 Hub면 Hub Service가 404 → FeignException.NotFound 발생
    @GetMapping("/hubs/{hubId}")
    ApiResponse<HubResponse> getHub(@PathVariable("hubId") UUID hubId);
}
