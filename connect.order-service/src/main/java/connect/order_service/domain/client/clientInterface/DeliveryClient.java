package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.request.DeliveryCreateRequest;
import connect.order_service.domain.client.dto.request.DeliveryReceiverRequest;
import connect.order_service.domain.client.dto.response.DeliveryInfo;

import java.util.UUID;

public interface DeliveryClient {
    UUID createDelivery(DeliveryCreateRequest request);   // 담당자 배정은 delivery 서비스가 처리

    DeliveryInfo getDelivery(UUID deliveryId, UUID userId, String userRole);

    void changeReceiver(UUID deliveryId, UUID userId, String role, DeliveryReceiverRequest request);

    void cancelDelivery(UUID orderId);
}
