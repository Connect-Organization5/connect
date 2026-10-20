package connect.delivery_service.client.user;

import connect.delivery_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

// primary = false: mock-user 프로필에서 MockUserClient(@Primary)가 대신 주입되도록
@FeignClient(name = "user-service", primary = false)
public interface UserClient {
    @GetMapping("/internal/users/hub/{departureHubId}/delivery-users")
    ApiResponse<List<UUID>> getHubDeliveryUserIds(@PathVariable("departureHubId") UUID departureHubId);
}
