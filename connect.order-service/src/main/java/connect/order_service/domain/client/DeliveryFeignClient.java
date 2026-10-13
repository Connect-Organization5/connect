package connect.order_service.domain.client;

import connect.order_service.domain.client.clientInterface.DeliveryClient;
import connect.order_service.domain.client.dto.DeliveryCreateRequest;
import connect.order_service.domain.client.dto.DeliveryInfo;
import connect.order_service.domain.client.dto.DeliveryUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

//@FeignClient(name = "delivery-service")
@Component
public class DeliveryFeignClient implements DeliveryClient {
    @Override
    public UUID createDelivery(DeliveryCreateRequest request) {
        return UUID.randomUUID();
    }

    @Override
    public DeliveryInfo getDelivery(UUID deliveryId) {
        return new DeliveryInfo(deliveryId, "READY");   // "IN_DELIVERY"로 바꾸면 거절 케이스 테스트 가능
    }

    @Override
    public void updateDelivery(UUID deliveryId, DeliveryUpdateRequest request) {

    }

    @Override
    public void cancelDelivery(UUID deliveryId) {

    }
}
