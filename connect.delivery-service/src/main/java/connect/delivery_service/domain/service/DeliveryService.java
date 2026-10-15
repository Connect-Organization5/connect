package connect.delivery_service.domain.service;

import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    public DeliveryResponse createDelivery(CreateDeliveryRequest request) {
        Optional<Delivery> delivery = deliveryRepository.findByOrderId(request.getOrderId());
        if (delivery.isPresent()) {
            return DeliveryResponse.from(delivery.get());
        }

        Delivery createdDelivery = Delivery.createDelivery(request.getOrderId(), request.getDeliveryUserId(), request.getDepartureHubId(),  request.getArrivalHubId(),
                request.getReceiver(), request.getDeliveryAddress());

        return DeliveryResponse.from(createdDelivery);
    }
}
