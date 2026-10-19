package connect.order_service.domain.client;

import connect.order_service.domain.client.Api.DeliveryFeignApi;
import connect.order_service.domain.client.clientInterface.DeliveryClient;
import connect.order_service.domain.client.dto.request.DeliveryCreateRequest;
import connect.order_service.domain.client.dto.request.DeliveryReceiverRequest;
import connect.order_service.domain.client.dto.response.DeliveryInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryFeignClient implements DeliveryClient {
    private final DeliveryFeignApi deliveryFeignApi;

    @Override
    public UUID createDelivery(DeliveryCreateRequest request) {
        return deliveryFeignApi.createDelivery(request).id();
    }

    @Override
    public DeliveryInfo getDelivery(UUID deliveryId, UUID userId, String userRole) {
        return deliveryFeignApi.getDelivery(deliveryId, userId, userRole);
    }

    @Override
    public void changeReceiver(UUID deliveryId, UUID userId, String userRole, DeliveryReceiverRequest request) {
        deliveryFeignApi.changeReceiver(deliveryId, userId, userRole, request);
    }

    @Override
    public void cancelDelivery(UUID orderId) {
        deliveryFeignApi.cancelDelivery(orderId);
    }
}
