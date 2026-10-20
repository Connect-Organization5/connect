package connect.delivery_service.domain.controller;

import connect.delivery_service.client.user.AffiliationType;
import connect.delivery_service.client.user.RoleType;
import connect.delivery_service.domain.dto.*;
import connect.delivery_service.domain.controller.docs.DeliveryControllerDocs;
import connect.delivery_service.domain.service.DeliveryService;
import connect.delivery_service.global.dto.ApiResponse;
import connect.delivery_service.global.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/deliveries")
@RequiredArgsConstructor
public class DeliveryController implements DeliveryControllerDocs {
    private final DeliveryService deliveryService;

    // 배송 단건 조회
    @GetMapping("/{deliveryId}")
    public ResponseEntity<ApiResponse<DeliveryResponse>> getDelivery(
            @PathVariable("deliveryId") UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        DeliveryResponse response = deliveryService.getDelivery(deliveryId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("배송이 조회되었습니다.", response));
    }

    // 배송 목록 조회, 검색
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DeliveryResponse>>> searchDeliveries(
            @ModelAttribute DeliverySearchCondition cond,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Affiliation-Type") AffiliationType affiliationType) {
        PagedResponse<DeliveryResponse> response = deliveryService.searchDeliveries(cond, userId, affiliationType);
        return ResponseEntity.ok(ApiResponse.ok("배송 목록이 조회되었습니다.", response));
    }

    // 담당자 미배정 배송 목록 조회
    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<PagedResponse<DeliveryResponse>>> getUnassignedDeliveries(
            @ModelAttribute DeliverySearchCondition cond,
            @RequestHeader("X-User-Affiliation-Id") UUID affiliationId,
            @RequestHeader("X-User-Affiliation-Type") AffiliationType affiliationType) {
        PagedResponse<DeliveryResponse> response = deliveryService.getUnassignedDeliveries(affiliationId, affiliationType, cond.toPageable());
        return ResponseEntity.ok(ApiResponse.ok("미배정 배송 목록이 조회되었습니다.", response));
    }

    // 배송 상태 변경
    @PostMapping("/{deliveryId}/hub-departure")
    public ResponseEntity<ApiResponse<Void>> startHubMoving(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        deliveryService.startHubMoving(deliveryId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("허브 출발 처리되었습니다.", null));
    }

    @PostMapping("/{deliveryId}/hub-arrival")
    public ResponseEntity<ApiResponse<Void>> arriveAtDestinationHub(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role,
            @Valid @RequestBody ArriveHubRequest request) {
        deliveryService.arriveAtDestinationHub(deliveryId, userId, role, request.getActualDistance());
        return ResponseEntity.ok(ApiResponse.ok("허브 도착 처리되었습니다.", null));
    }

    @PostMapping("/{deliveryId}/delivery-start")
    public ResponseEntity<ApiResponse<Void>> startDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        deliveryService.startDelivery(deliveryId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("배송 출발 처리되었습니다.", null));
    }

    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<ApiResponse<Void>> completeDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        deliveryService.completeDelivery(deliveryId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("배송 완료 처리되었습니다.", null));
    }


    // 배송 담당자 배정 (요청한 본인이 배정)
    @PostMapping("/{deliveryId}/delivery-user")
    public ResponseEntity<ApiResponse<DeliveryResponse>> assignDeliveryUserId(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Affiliation-Id") UUID affiliationId,
            @RequestHeader("X-User-Affiliation-Type") AffiliationType affiliationType) {
        DeliveryResponse response = deliveryService.assignDeliveryUserId(deliveryId, userId, affiliationId, affiliationType);
        return ResponseEntity.ok(ApiResponse.ok("배송 담당자가 배정되었습니다.", response));
    }

    // 배송 담당자 변경
    @PatchMapping("/{deliveryId}/delivery-user")
    public ResponseEntity<ApiResponse<DeliveryResponse>> changeDeliveryUserId(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Role") RoleType role,
            @Valid @RequestBody ChangeDeliveryUserRequest request) {
        DeliveryResponse response = deliveryService.changeDeliveryUserId(deliveryId, role, request.getDeliveryUserId());
        return ResponseEntity.ok(ApiResponse.ok("배송 담당자가 변경되었습니다.", response));
    }

    // 배송 수령인 변경
    @PatchMapping("/{deliveryId}/receiver")
    public ResponseEntity<ApiResponse<DeliveryResponse>> changeReceiver(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role,
            @Valid @RequestBody ChangeReceiverRequest request) {
        DeliveryResponse response = deliveryService.changeReceiver(deliveryId, userId, role, request.getReceiver());
        return ResponseEntity.ok(ApiResponse.ok("수령인이 변경되었습니다.", response));
    }

    @DeleteMapping("/{deliveryId}")
    public ResponseEntity<ApiResponse<Void>> deleteDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        deliveryService.deleteDelivery(deliveryId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("배송이 삭제되었습니다.", null));
    }

    // 배송 경로 기록 조회
    @GetMapping("/routes/{deliveryId}")
    public ResponseEntity<ApiResponse<DeliveryRouteResponse>> getDeliveryRoute(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        DeliveryRouteResponse response = deliveryService.getDeliveryRoute(deliveryId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("배송 경로가 조회되었습니다.", response));
    }
}
