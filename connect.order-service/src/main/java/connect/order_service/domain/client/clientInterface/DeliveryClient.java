package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.response.DeliveryCreateRequest;
import connect.order_service.domain.client.dto.response.DeliveryInfo;
import connect.order_service.domain.client.dto.response.DeliveryUpdateRequest;

import java.util.UUID;

public interface DeliveryClient {
    UUID createDelivery(DeliveryCreateRequest request);   // 담당자 배정은 delivery 서비스가 처리

    DeliveryInfo getDelivery(UUID deliveryId);

    void updateDelivery(UUID deliveryId, DeliveryUpdateRequest request);

    void cancelDelivery(UUID deliveryId);
}
