package connect.order_service.domain.controller;

import connect.order_service.domain.controller.docs.OrderControllerDocs;
import connect.order_service.domain.dto.CreateOrderRequest;
import connect.order_service.domain.dto.OrderResponse;
import connect.order_service.domain.dto.UpdateOrderRequest;
import connect.order_service.domain.service.OrderService;
import connect.order_service.global.dto.ApiResponse;
import connect.order_service.global.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Affiliation-Type") String userAffiliationType,
            @RequestHeader("X-User-Affiliation-Id") UUID userAffiliationId,
            @RequestBody CreateOrderRequest request) {   // @Valid 는 OrderControllerDocs 에 선언 (구현 메서드에 다시 선언하면 HV000151 오류)

        OrderResponse response = orderService.createOrder(userId, userAffiliationType, userAffiliationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("주문이 생성되었습니다.", response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {

        OrderResponse response = orderService.searchOrder(userId, role, orderId);
        return ResponseEntity.ok(ApiResponse.ok("주문 조회에 성공했습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getOrders(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,

            // page·size 범위 검증(@Min/@Max)은 OrderControllerDocs 에 선언 (구현 메서드에 다시 선언하면 Hibernate Validator HV000151 오류)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PagedResponse<OrderResponse> response = orderService.searchPageOrders(userId, role, page, size);
        return ResponseEntity.ok(ApiResponse.ok("주문 목록 조회에 성공했습니다.", response));
    }

    @GetMapping("/received/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getReceivedOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {

        OrderResponse response = orderService.searchReceivedOrder(userId, role, orderId);
        return ResponseEntity.ok(ApiResponse.ok("주문 받은 내용 조회에 성공했습니다.", response));
    }

    @GetMapping("/received")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getReceivedOrders(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Affiliation-Type") String userAffiliationType,
            @RequestHeader("X-User-Affiliation-Id") UUID userAffiliationId,
            // page·size 범위 검증(@Min/@Max)은 OrderControllerDocs 에 선언 (구현 메서드에 다시 선언하면 Hibernate Validator HV000151 오류)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PagedResponse<OrderResponse> response = orderService.searchReceivedPageOrders(userId, role, userAffiliationType, userAffiliationId, page, size);
        return ResponseEntity.ok(ApiResponse.ok("주문 받은 목록 조회에 성공했습니다.", response));
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId,
            @RequestBody UpdateOrderRequest request) {   // @Valid 는 OrderControllerDocs 에 선언 (구현 메서드에 다시 선언하면 HV000151 오류)

        OrderResponse response = orderService.updateOrder(userId, role, orderId, request);
        return ResponseEntity.ok(ApiResponse.ok("주문이 수정되었습니다.", response));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {

        orderService.deleteOrder(userId, role, orderId);
        return ResponseEntity.ok(ApiResponse.ok("주문이 취소되었습니다.", null));
    }
}
