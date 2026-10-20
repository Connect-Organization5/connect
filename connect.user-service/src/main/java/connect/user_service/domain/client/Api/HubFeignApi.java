package connect.user_service.domain.client.Api;


import connect.user_service.domain.user.dto.response.HubInfo;
import connect.user_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "hub-service")
public interface HubFeignApi {

    @GetMapping("/hubs/{hubId}")
    ApiResponse<HubInfo> getHub(@PathVariable("hubId") UUID hubId);
}
