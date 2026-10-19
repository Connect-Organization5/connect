package connect.order_service.domain.client.Api;

import connect.order_service.domain.client.dto.response.UserInfo;
import connect.order_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserFeignApi {

    @GetMapping("/internal/users/{userId}")
    ApiResponse<UserInfo> getUser(@PathVariable UUID userId);
}

