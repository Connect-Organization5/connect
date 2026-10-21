package connect.delivery_service.domain.service;

import connect.delivery_service.client.hub.HubClient;
import connect.delivery_service.client.hub.HubRouteResponse;
import connect.delivery_service.client.user.AffiliationType;
import connect.delivery_service.client.user.RoleType;
import connect.delivery_service.client.user.UserClient;
import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.domain.dto.DeliveryRouteResponse;
import connect.delivery_service.domain.dto.DeliverySearchCondition;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryRoute;
import connect.delivery_service.domain.entity.DeliveryStatus;
import connect.delivery_service.domain.repository.DeliveryRepository;
import connect.delivery_service.domain.repository.DeliveryRepositoryCustomImpl;
import connect.delivery_service.domain.repository.DeliveryRouteRepository;
import connect.delivery_service.global.dto.ApiResponse;
import connect.delivery_service.global.dto.PagedResponse;
import connect.delivery_service.global.exception.BaseException;
import connect.delivery_service.global.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository deliveryRouteRepository;
    private final HubClient hubClient;
    private final UserClient userClient;

    // 한 담당자에게 묶을 수 있는 같은 구간(출발·도착 허브) 출발 전 배송 수
    @Value("${delivery.assignment.bundle-max-size:5}")
    private int bundleMaxSize;

    //배송 생성
    public DeliveryResponse createDelivery(CreateDeliveryRequest request) {

        Optional<Delivery> delivery = deliveryRepository.findByOrderId(request.getOrderId());
        if (delivery.isPresent()) {
            return DeliveryResponse.from(delivery.get());
        }

        HubRouteResponse hubRoute = getHubRoute(request.getDepartureHubId(), request.getArrivalHubId());

        Delivery createdDelivery = Delivery.createDelivery(request.getOrderId(), request.getOrdererId(), request.getDepartureHubId(), request.getArrivalHubId(), request.getReceiver(), request.getDeliveryAddress());
        deliveryRepository.save(createdDelivery);

        DeliveryRoute createdDeliveryRoute = DeliveryRoute.createDeliveryRoute(createdDelivery.getId(), createdDelivery.getDeliveryUserId(), createdDelivery.getDepartureHubId(), createdDelivery.getArrivalHubId(), hubRoute.distance(), hubRoute.duration());
        deliveryRouteRepository.save(createdDeliveryRoute);

        autoAssignDeliveryUserId(createdDelivery, createdDeliveryRoute);

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

    @Transactional(readOnly = true)
    public PagedResponse<DeliveryResponse> getUnassignedDeliveries(UUID affiliationId, AffiliationType affiliationType, Pageable pageable) {
        if (affiliationType != AffiliationType.HUB) {
            throw new BaseException(ErrorCode.DELIVERY_ACCESS_DENIED);
        }
        Page<Delivery> page = deliveryRepository.findAllByDeliveryUserIdIsNullAndDeliveryStatusAndDepartureHubId(
                DeliveryStatus.HUB_WAITING, affiliationId, pageable
        );
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
    public void startHubMoving(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId, role);
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));

        delivery.startHubMoving();
        deliveryRoute.startMoving(LocalDateTime.now());
    }

    public void arriveAtDestinationHub(UUID deliveryId, UUID userId, RoleType role, BigDecimal actualDistance) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId, role);
        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));

        deliveryRoute.complete(actualDistance, LocalDateTime.now());
        delivery.arrivedHub();
    }

    public void startDelivery(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId, role);
        delivery.startDelivery();
    }

    public void completeDelivery(UUID deliveryId, UUID userId, RoleType role) {
        Delivery delivery = checkForDeliveryStatusUpdate(deliveryId, userId, role);
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

    // 배송 담당자 배정 (요청한 본인이 배정)
    public DeliveryResponse assignDeliveryUserId(UUID deliveryId, UUID userId, UUID affiliationId, AffiliationType affiliationType) {
        if (affiliationType != AffiliationType.HUB) {
            throw new BaseException(ErrorCode.DELIVERY_ACCESS_DENIED);
        }
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
        // 출발 허브 소속만 배정 가능
        if (!affiliationId.equals(delivery.getDepartureHubId())) {
            throw new BaseException(ErrorCode.DELIVERY_HUB_MISMATCH);
        }
        delivery.assignDeliveryUserId(userId);

        DeliveryRoute deliveryRoute = deliveryRouteRepository.findByDeliveryId(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_ROUTE_NOT_FOUND));
        deliveryRoute.assignDeliveryUserId(userId);

        return DeliveryResponse.from(delivery);
    }

    // 배송 담당자 배정 (시스템 배정)
    private void autoAssignDeliveryUserId(Delivery delivery, DeliveryRoute deliveryRoute) {
        List<UUID> hubUserIds;
        // 출발 허브 배송 담당자 조회 실패시 미배정
        try {
            hubUserIds = userClient.getHubDeliveryUserIds(delivery.getDepartureHubId()).getData();
        } catch (FeignException e) {
            return;
        }

        // 출발 허브에 배송 담당자가 없으면 미배정
        if (hubUserIds == null || hubUserIds.isEmpty()) {
            return;
        }

        Map<UUID, DeliveryRepositoryCustomImpl.DriverLoad> loads = deliveryRepository.findDriverLoads(hubUserIds);

        // 부하 분산 기준: 진행 중 배송이 적은 담당자 → 같으면 마지막 배정이 오래된(이력 없으면 최우선) 담당자
        Comparator<UUID> byLoad = Comparator
                .comparingLong((UUID id) -> loads.containsKey(id) ? loads.get(id).activeCount() : 0L)
                .thenComparing(
                        id -> loads.containsKey(id) ? loads.get(id).lastAssignedAt() : null,
                        Comparator.nullsFirst(Comparator.naturalOrder()));

        // 같은 구간(출발·도착 허브)에서 담당자별로 맡고 있는 출발 전 배송 수
        Map<UUID, Long> bundleCounts = deliveryRepository.countWaitingDeliveriesOnRoute(
                delivery.getDepartureHubId(), delivery.getArrivalHubId(), hubUserIds);

        // 같은 구간의 출발 전 배송을 맡은 담당자가 있으면 묶어서 배정, 없으면 부하 분산
        UUID selected = findBundleDeliveryUserId(bundleCounts, byLoad)
                .orElseGet(() -> withinBundleLimit(hubUserIds, bundleCounts).stream().min(byLoad).orElseThrow());

        delivery.assignDeliveryUserId(selected);
        deliveryRoute.assignDeliveryUserId(selected);
    }

    // 묶음 배정 대상: 같은 구간의 HUB_WAITING 배송을 맡고 있고, 그 구간 배송이 상한(bundleMaxSize) 미만인 담당자
    // 후보가 여럿이면 이미 묶인 배송이 많은 담당자 → 같으면 부하 분산 기준
    private Optional<UUID> findBundleDeliveryUserId(Map<UUID, Long> bundleCounts, Comparator<UUID> byLoad) {
        return bundleCounts.entrySet().stream()
                .filter(e -> e.getValue() < bundleMaxSize)
                .map(Map.Entry::getKey)
                .min(Comparator.comparingLong((UUID id) -> bundleCounts.get(id)).reversed()
                        .thenComparing(byLoad));
    }

    // 부하 분산 후보: 그 구간에서 상한에 도달한 담당자는 제외 (전원이 상한이면 전원). bundleMaxSize 가 0 이면 묶음 배정을 끈 것이라 제외하지 않음
    private List<UUID> withinBundleLimit(List<UUID> hubUserIds, Map<UUID, Long> bundleCounts) {
        if (bundleMaxSize <= 0) {
            return hubUserIds;
        }
        List<UUID> candidates = hubUserIds.stream()
                .filter(id -> bundleCounts.getOrDefault(id, 0L) < bundleMaxSize)
                .toList();
        return candidates.isEmpty() ? hubUserIds : candidates;
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

    private Delivery checkForDeliveryStatusUpdate(UUID deliveryId, UUID userId, RoleType roleType) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new BaseException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!(delivery.isManagedBy(userId) || roleType == RoleType.MASTER)) {
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
