package connect.delivery_service.domain.controller;

import connect.delivery_service.domain.controller.docs.InternalDeliveryControllerDocs;
import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.domain.service.DeliveryService;
import connect.delivery_service.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/deliveries")
@RequiredArgsConstructor
public class InternalDeliveryController implements InternalDeliveryControllerDocs {
    private final DeliveryService deliveryService;

    // 배송 생성 (주문 생성 시 호출)
    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryResponse>> createDelivery(@Valid @RequestBody CreateDeliveryRequest request) {
        DeliveryResponse response = deliveryService.createDelivery(request);
        return ResponseEntity.ok(ApiResponse.ok("배송이 생성되었습니다.", response));
    }

    // 배송 취소 (주문 삭제 시 호출)
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelByOrderId(@RequestParam UUID orderId) {
        deliveryService.cancelByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.ok("배송이 취소되었습니다.", null));
    }
}
