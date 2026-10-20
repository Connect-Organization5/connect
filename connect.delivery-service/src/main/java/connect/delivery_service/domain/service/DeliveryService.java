package connect.delivery_service.domain.service;

import connect.delivery_service.client.AffiliationType;
import connect.delivery_service.client.HubClient;
import connect.delivery_service.client.HubRouteResponse;
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
import connect.delivery_service.global.dto.ApiResponse;
import connect.delivery_service.global.dto.PagedResponse;
import connect.delivery_service.global.exception.BaseException;
import connect.delivery_service.global.exception.ErrorCode;
import feign.FeignException;
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
    private final HubClient hubClient;

    //배송 생성
    public DeliveryResponse createDelivery(CreateDeliveryRequest request) {

        Optional<Delivery> delivery = deliveryRepository.findByOrderId(request.getOrderId());
        if (delivery.isPresent()) {
            return DeliveryResponse.from(delivery.get());
        }

        // 경로가 없으면 배송도 만들지 않도록 저장 전에 허브 경로를 먼저 조회한다
        HubRouteResponse hubRoute = getHubRoute(request.getDepartureHubId(), request.getArrivalHubId());

        Delivery createdDelivery = Delivery.createDelivery(request.getOrderId(), request.getOrdererId(), request.getDepartureHubId(), request.getArrivalHubId(), request.getReceiver(), request.getDeliveryAddress());
        deliveryRepository.save(createdDelivery);

        DeliveryRoute createdDeliveryRoute = DeliveryRoute.createDeliveryRoute(createdDelivery.getId(), createdDelivery.getDeliveryUserId(), createdDelivery.getDepartureHubId(), createdDelivery.getArrivalHubId(), hubRoute.distance(), hubRoute.duration());
        deliveryRouteRepository.save(createdDeliveryRoute);

        return DeliveryResponse.from(createdDelivery);
    }

    // 배송 단건 조회
    @Transactional(readOnly = true)
    public DeliveryResponse getDelivery(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!(delivery.isOrderedBy(userId) || delivery.isManagedBy(userId) || role == RoleType.MASTER)) {
            throw new BaseException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return DeliveryResponse.from(delivery);
    }

    // 배송 목록 조회, 검색
    @Transactional(readOnly = true)
    public PagedResponse<DeliveryResponse> searchDeliveries(DeliverySearchCondition cond, UUID userId, AffiliationType affiliationType) {
        Page<Delivery> page = deliveryRepository.search(cond, userId, affiliationType, cond.toPageable());
        return PagedResponse.from(page.map(DeliveryResponse::from));
    }

    // 배송 경로 기록 조회
    @Transactional(readOnly = true)
    public DeliveryRouteResponse getDeliveryRoute(UUID deliveryId, UUID userId, RoleType role) {
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));

        boolean isRouteManager = userId.equals(deliveryRoute.getDeliveryUserId());
        if (!(isRouteManager || role == RoleType.MASTER)) {
            Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
            if (!delivery.isOrderedBy(userId)) {
                throw new BaseException(ErrorCode.DELIVERY_NOT_FOUND);
            }
        }
        return DeliveryRouteResponse.from(deliveryRoute);
    }

    // 배송 상태 변경
    public void startHubMoving(UUID deliveryId, UUID userId) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId);
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));

        delivery.startHubMoving();
        deliveryRoute.startMoving();
    }

    public void arriveAtDestinationHub(UUID deliveryId, UUID userId) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId);
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));

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

    // 배송 담당자 배정 (요청한 본인이 배정)
    public DeliveryResponse assignDeliveryUserId(UUID deliveryId, UUID userId, AffiliationType affiliationType) {
        if (affiliationType != AffiliationType.HUB) {
            throw new BaseException(ErrorCode.DELIVERY_ACCESS_DENIED);
        }
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
        delivery.assignDeliveryUserId(userId);

        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));
        deliveryRoute.assignDeliveryUserId(userId);

        return DeliveryResponse.from(delivery);
    }

    // 배송 담당자 변경
    public DeliveryResponse changeDeliveryUserId(UUID deliveryId, RoleType role, UUID deliveryUserId) {
        if (role != RoleType.MASTER) {
            throw new BaseException(ErrorCode.DELIVERY_ACCESS_DENIED);
        }
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
        delivery.changeDeliveryUserId(deliveryUserId);

        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));
        deliveryRoute.changeDeliveryUserId(deliveryUserId);

        return DeliveryResponse.from(delivery);
    }

    // 배송 수령인 변경
    public DeliveryResponse changeReceiver(UUID deliveryId, UUID userId, RoleType role, String receiver) {
        Delivery delivery = checkForOrdererOrMaster(deliveryId, userId, role);
        delivery.changeReceiver(receiver);
        return DeliveryResponse.from(delivery);
    }

    // 배송 삭제
    public void deleteDelivery(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = checkForOrdererOrMaster(deliveryId, userId, role);
        delivery.delete(userId);

        deliveryRouteRepository.findByDeliveryId(deliveryId)
                .ifPresent(deliveryRoute -> deliveryRoute.delete(userId));
    }


    private HubRouteResponse getHubRoute(UUID departureHubId, UUID arrivalHubId) {
        ApiResponse<HubRouteResponse> response;
        try {
            response = hubClient.getRoute(departureHubId, arrivalHubId);
        } catch (FeignException.NotFound e) {
            throw new BaseException(ErrorCode.HUB_ROUTE_NOT_FOUND);
        } catch (FeignException e) {
            throw new BaseException(ErrorCode.HUB_SERVICE_UNAVAILABLE);
        }

        if (response == null || response.getData() == null) {
            throw new BaseException(ErrorCode.HUB_ROUTE_NOT_FOUND);
        }
        return response.getData();
    }

    private Delivery checkForDeliveryStatusUpdate(UUID deliveryId, UUID userId) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!delivery.isManagedBy(userId)) {
            throw new BaseException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return delivery;
    }

    private Delivery checkForOrdererOrMaster(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!(delivery.isOrderedBy(userId) || role == RoleType.MASTER)) {
            throw new BaseException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return delivery;
    }

}
