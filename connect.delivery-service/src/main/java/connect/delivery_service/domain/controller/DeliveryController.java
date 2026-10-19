package connect.delivery_service.domain.controller;

import connect.delivery_service.client.AffiliationType;
import connect.delivery_service.client.RoleType;
import connect.delivery_service.domain.dto.*;
import connect.delivery_service.domain.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/deliveries")
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService deliveryService;

    @PostMapping
    public ResponseEntity<DeliveryResponse> createDelivery(@RequestBody CreateDeliveryRequest request) {
        return ResponseEntity.ok(deliveryService.createDelivery(request));
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(
            @PathVariable("deliveryId") UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        return ResponseEntity.ok(deliveryService.getDelivery(deliveryId, userId, role));
    }

    @GetMapping
    public ResponseEntity<Page<DeliveryResponse>> searchDeliveries(
            @ModelAttribute DeliverySearchCondition cond,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-affiliationType") AffiliationType affiliationType) {
        return ResponseEntity.ok(deliveryService.searchDeliveries(cond, userId, affiliationType));
    }

    @PostMapping("/{deliveryId}/hub-departure")
    public ResponseEntity<Void> startHubMoving(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId) {
        deliveryService.startHubMoving(deliveryId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deliveryId}/hub-arrival")
    public ResponseEntity<Void> arriveAtDestinationHub(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId) {
        deliveryService.arriveAtDestinationHub(deliveryId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deliveryId}/delivery-start")
    public ResponseEntity<Void> startDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId) {
        deliveryService.startDelivery(deliveryId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<Void> completeDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId) {
        deliveryService.completeDelivery(deliveryId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelByOrderId(@RequestParam UUID orderId) {
        deliveryService.cancelByOrderId(orderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deliveryId}/delivery-user")
    public ResponseEntity<DeliveryResponse> assignDeliveryUserId(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-affiliationType") AffiliationType affiliationType) {
        return ResponseEntity.ok(deliveryService.assignDeliveryUserId(deliveryId, userId, affiliationType));
    }

    @PatchMapping("/{deliveryId}/delivery-user")
    public ResponseEntity<DeliveryResponse> changeDeliveryUserId(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Role") RoleType role,
            @RequestBody ChangeDeliveryUserRequest request) {
        return ResponseEntity.ok(deliveryService.changeDeliveryUserId(deliveryId, role, request.getDeliveryUserId()));
    }

    @PatchMapping("/{deliveryId}/receiver")
    public ResponseEntity<DeliveryResponse> changeReceiver(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role,
            @RequestBody ChangeReceiverRequest request) {
        return ResponseEntity.ok(deliveryService.changeReceiver(deliveryId, userId, role, request.getReceiver()));
    }

    @DeleteMapping("/{deliveryId}")
    public ResponseEntity<Void> deleteDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        deliveryService.deleteDelivery(deliveryId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/routes/{deliveryId}")
    public ResponseEntity<DeliveryRouteResponse> getDeliveryRoute(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") RoleType role) {
        return ResponseEntity.ok(deliveryService.getDeliveryRoute(deliveryId, userId, role));
    }
}
