package connect.order_service.domain.client.Api;

import connect.order_service.domain.client.dto.request.DeliveryCreateRequest;
import connect.order_service.domain.client.dto.request.DeliveryReceiverRequest;
import connect.order_service.domain.client.dto.response.DeliveryInfo;
import connect.order_service.global.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "delivery-service")
public interface DeliveryFeignApi {

    @PostMapping("/deliveries")
    ApiResponse<DeliveryInfo> createDelivery(@RequestBody DeliveryCreateRequest request);

    @GetMapping("/deliveries/{deliveryId}")
    ApiResponse<DeliveryInfo> getDelivery(@PathVariable("deliveryId") UUID deliveryId,
                             @RequestHeader("X-User-Id") UUID userId,
                             @RequestHeader("X-User-Role") String role);

    @PatchMapping("/deliveries/{deliveryId}/receiver")
    void changeReceiver(@PathVariable("deliveryId") UUID deliveryId,
                        @RequestHeader("X-User-Id") UUID userId,
                        @RequestHeader("X-User-Role") String role,
                        @RequestBody DeliveryReceiverRequest request);

    @PostMapping("/deliveries/cancel")
    void cancelDelivery(@RequestParam("orderId") UUID orderId);
}
