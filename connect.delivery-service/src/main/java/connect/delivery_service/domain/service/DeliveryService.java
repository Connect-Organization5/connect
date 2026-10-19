package connect.delivery_service.domain.service;

import connect.delivery_service.client.AffiliationType;
import connect.delivery_service.client.RoleType;
import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.domain.dto.DeliveryRouteResponse;
import connect.delivery_service.domain.dto.DeliverySearchCondition;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryRoute;
import connect.delivery_service.domain.entity.DeliveryStatus;
import connect.delivery_service.domain.repository.DeliveryRepository;
import connect.delivery_service.domain.repository.DeliveryRouteRepository;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository deliveryRouteRepository;

    //배송 생성
    public DeliveryResponse createDelivery(CreateDeliveryRequest request) {

        Optional<Delivery> delivery = deliveryRepository.findByOrderId(request.getOrderId());
        if (delivery.isPresent()) {
            return DeliveryResponse.from(delivery.get());
        }

        Delivery createdDelivery = Delivery.createDelivery(request.getOrderId(), request.getOrdererId(), request.getDepartureHubId(), request.getArrivalHubId(), request.getReceiver(), request.getDeliveryAddress());
        deliveryRepository.save(createdDelivery);

        // hubClient.getRoute(departureHubId, arrivalHubId)
        BigDecimal estimatedDistance = BigDecimal.valueOf(18);
        Integer estimatedDuration = 18;

        DeliveryRoute createdDeliveryRoute = DeliveryRoute.createDeliveryRoute(createdDelivery.getId(), createdDelivery.getDeliveryUserId(), createdDelivery.getDepartureHubId(), createdDelivery.getArrivalHubId(), estimatedDistance, estimatedDuration);
        deliveryRouteRepository.save(createdDeliveryRoute);

        return DeliveryResponse.from(createdDelivery);
    }

    //단건 조회
    @Transactional(readOnly = true)
    public DeliveryResponse getDelivery(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new NotFoundException("배송을 찾을 수 없습니다."));
        if (!(delivery.isOrderedBy(userId) || delivery.isManagedBy(userId) || role == RoleType.MASTER)) {
            throw new NotFoundException("404");
        }
        return DeliveryResponse.from(delivery);
    }

    //목록 조회, 검색
    @Transactional(readOnly = true)
    public Page<DeliveryResponse> searchDeliveries(DeliverySearchCondition cond, UUID userId, AffiliationType affiliationType) {
        Page<Delivery> page = deliveryRepository.search(cond, userId, affiliationType, cond.toPageable());
        return page.map(DeliveryResponse::from);
    }

    //배송 경로 기록 조회
    @Transactional(readOnly = true)
    public DeliveryRouteResponse getDeliveryRoute(UUID deliveryId, UUID userId, RoleType role) {
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new NotFoundException("404"));

        boolean isRouteManager = deliveryRoute.getDeliveryUserId().equals(userId);
        if (!(isRouteManager || role == RoleType.MASTER)) {
            Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new NotFoundException("404"));
            if (!delivery.isOrderedBy(userId)) {
                throw new NotFoundException("404");
            }
        }
        return DeliveryRouteResponse.from(deliveryRoute);
    }

    // 배송 상태 변경
    public void startHubMoving(UUID deliveryId, UUID userId) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId);
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new NotFoundException("404"));

        delivery.startHubMoving();
        deliveryRoute.startMoving();
    }

    public void arriveAtDestinationHub(UUID deliveryId, UUID userId) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId);
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new NotFoundException("404"));

        // 실제 이동거리, 소요시간 임시값
        deliveryRoute.complete(BigDecimal.valueOf(18), 18);
        delivery.arrivedHub();
    }

    public void startDelivery(UUID deliveryId, UUID userId) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId);
        delivery.startDelivery();
    }

    public void completeDelivery(UUID deliveryId, UUID userId) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId);
        delivery.complete();
    }

    public void cancelByOrderId(UUID orderId) {
        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> {
            if (delivery.getDeliveryStatus() == DeliveryStatus.CANCELED) {
                return;
            }
            delivery.cancel();
            deliveryRouteRepository.findByDeliveryId(delivery.getId())
                    .ifPresent(DeliveryRoute::cancel);
        });
    }


    //배송 담당자 변경
    public DeliveryResponse changeDeliveryUserId(UUID deliveryId, RoleType role, UUID deliveryUserId) {
        if (role != RoleType.MASTER) {
            throw new ForbiddenException("403");
        }
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new NotFoundException("404"));
        delivery.changeDeliveryUserId(deliveryUserId);

        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId).orElseThrow(() -> new NotFoundException("404"));
        deliveryRoute.changeDeliveryUserId(deliveryUserId);

        return DeliveryResponse.from(delivery);
    }

    //배송 수령인 변경
    public DeliveryResponse changeReceiver(UUID deliveryId, UUID userId, RoleType role, String receiver) {
        Delivery delivery = checkForOrdererOrMaster(deliveryId, userId, role);
        delivery.changeReceiver(receiver);
        return DeliveryResponse.from(delivery);
    }


    public void deleteDelivery(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = checkForOrdererOrMaster(deliveryId, userId, role);
        delivery.delete(userId);

        deliveryRouteRepository.findByDeliveryId(deliveryId)
                .ifPresent(deliveryRoute -> deliveryRoute.delete(userId));
    }


    private Delivery checkForDeliveryStatusUpdate(UUID deliveryId, UUID userId) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new NotFoundException("404"));
        if (!delivery.isManagedBy(userId)) {
            throw new NotFoundException("404");
        }
        return delivery;
    }

    private Delivery checkForOrdererOrMaster(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new NotFoundException("404"));
        if (!(delivery.isOrderedBy(userId) || role == RoleType.MASTER)) {
            throw new NotFoundException("404");
        }
        return delivery;
    }

}
