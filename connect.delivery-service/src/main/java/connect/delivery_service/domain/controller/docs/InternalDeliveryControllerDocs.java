package connect.delivery_service.domain.controller.docs;

import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.global.dto.ApiResponse;
import connect.delivery_service.global.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

// InternalDeliveryController 의 Swagger 문서 (동작은 InternalDeliveryController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Internal Delivery (내부 전용)", description = """
        **서비스 간 내부 호출 전용 API** (호출: order-service, Feign)
        - Gateway 라우트에 없어 외부 클라이언트는 호출할 수 없습니다.
        - 사용자 헤더(X-User-*)를 받지 않고 권한 검사를 하지 않습니다.
        """)
public interface InternalDeliveryControllerDocs {

    @Operation(
            summary = "[내부] 배송 생성",
            description = """
                    주문 생성 시 order-service 가 호출합니다.

                    1. 같은 `orderId` 로 이미 배송이 있으면 새로 만들지 않고 기존 배송을 반환합니다.
                    2. hub-service 에서 출발 → 도착 허브 경로를 조회해 예상 거리(km)·소요시간(분)을 배송 경로에 저장합니다.
                    3. 배송 담당자를 **시스템이 자동 배정**합니다. (user-service 에서 출발 허브의 배송 담당자 목록 조회)
                       - **묶음 배정**: 같은 출발·도착 허브의 `HUB_WAITING` 배송을 맡고 있는 담당자에게 우선 배정합니다.
                         그 구간 배송이 상한(`delivery.assignment.bundle-max-size`, 기본 5건) 미만인 담당자만 대상이며,
                         여럿이면 이미 묶인 배송이 많은 담당자를 고릅니다.
                       - 묶을 담당자가 없으면 **부하 분산**: 진행 중 배송이 가장 적은 담당자 → 같으면 마지막 배정이 오래된(이력이 없으면 최우선) 담당자 → 같으면 가입 순
                         (그 구간 상한에 도달한 담당자는 제외. 출발 허브 담당자가 모두 상한이면 전원 중에서 배정)
                       - 출발 허브에 담당자가 없거나 user-service 호출이 실패하면 **미배정**으로 생성합니다. (이후 담당자가 직접 배정)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공 (또는 기존 배송 반환)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "허브 간 경로 없음 — 출발·도착 허브가 같거나 존재하지 않는 허브 (HUB_ROUTE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "hub-service 호출 실패 (HUB_SERVICE_UNAVAILABLE)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<DeliveryResponse>> createDelivery(CreateDeliveryRequest request);

    @Operation(
            summary = "[내부] 주문 취소에 따른 배송 취소",
            description = """
                    주문 취소 시 order-service 가 호출합니다.

                    - 배송과 배송 경로를 `CANCELED` 로 바꿉니다. `HUB_WAITING` 상태에서만 취소할 수 있습니다.
                    - 이미 취소됐거나 해당 주문의 배송이 없으면 아무것도 하지 않고 200 을 반환합니다. (재시도해도 안전)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공 (또는 이미 취소됨)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 출발한 배송 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> cancelByOrderId(@Parameter(description = "주문 ID") UUID orderId);
}
