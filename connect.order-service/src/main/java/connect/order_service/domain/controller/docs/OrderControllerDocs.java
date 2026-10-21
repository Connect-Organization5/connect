package connect.order_service.domain.controller.docs;

import connect.order_service.domain.dto.CreateOrderRequest;
import connect.order_service.domain.dto.OrderResponse;
import connect.order_service.domain.dto.UpdateOrderRequest;
import connect.order_service.global.dto.ApiResponse;
import connect.order_service.global.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static connect.order_service.global.config.OpenApiConfig.GATEWAY_AUTH;

// OrderController 의 Swagger 문서 (동작은 OrderController 에 있고 여기에는 설명만 둔다)
// 실패 응답도 ApiResponse(success=false) 형식이라 오류 스키마는 ApiResponse 로 표시한다
// 주의: page·size 의 @Min/@Max 와 요청 본문의 @Valid 는 여기에만 선언한다. Hibernate Validator 는 구현 메서드가 파라미터 제약을 다시 선언하는 것을 허용하지 않는다 (HV000151)
@Tag(name = "Order", description = "주문 관리 API (Gateway 경유, X-User-* 헤더 필요)")
public interface OrderControllerDocs {

    @Operation(
            summary = "주문 생성",
            description = """
                    공급 업체의 상품을 주문합니다. 처리 순서는 다음과 같습니다.
                    1. 요청자 소속(`COMPANY` 면 업체, `HUB` 면 허브)을 조회해 도착 허브와 배송지를 정합니다.
                    2. 공급 업체의 상품인지 확인한 뒤 주문을 `PENDING` 으로 저장합니다.
                    3. 공급 업체 허브의 재고를 차감하고 배송을 생성하면 `CREATED` 가 됩니다.
                    4. 재고 차감·배송 생성·최종 저장에 실패하면 주문은 `FAILED` 로 남고 오류로 응답합니다.
                       이미 처리된 단계는 되돌리기를 시도합니다. (재고 복구, 배송 취소. 되돌리기 실패는 기록만 남김)
                    5. 성공 후 주문자와 공급 업체 담당자에게 Slack 알림을 비동기로 보냅니다. (알림 실패는 응답에 영향 없음)

                    **권한**: `X-User-Affiliation-Type` 이 `COMPANY` 또는 `HUB` 인 사용자
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·검증 실패, 필수 헤더 누락, UUID·본문 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "소속 유형이 COMPANY·HUB 가 아니거나 소속 업체·허브가 없음 (AFFILIATION_NOT_FOUND), 공급 업체의 상품이 아님 (PRODUCT_NOT_FOUND), 공급 업체 없음 (SUPPLIER_COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족 (OUT_OF_STOCK), 재고가 삭제된 상품 (PRODUCT_DISCONTINUED), 공급 허브에 재고가 등록되지 않은 상품 (PRODUCT_NOT_READY). 모두 주문은 FAILED",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "재고 차감 중 오류 (STOCK_DECREASE_FAILED), 배송 생성 실패 (DELIVERY_CREATE_FAILED), 주문 최종 저장 실패 (ORDER_SAVE_FAILED). 모두 주문은 FAILED",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "요청자 소속 업체·허브, 공급 업체, 상품 조회 중 404 외의 호출 실패 (company/hub-service 장애)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID (주문자, createdBy 로 저장)") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Affiliation-Type", required = true, description = "요청 사용자 소속 유형 (COMPANY 또는 HUB)", example = "COMPANY") String userAffiliationType,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Affiliation-Id", required = true, description = "요청 사용자 소속 업체 ID 또는 허브 ID") UUID userAffiliationId,
            @Valid CreateOrderRequest request);

    @Operation(
            summary = "주문 단건 조회",
            description = "삭제되지 않은 주문을 조회합니다. `FAILED` 주문도 조회됩니다.\n\n**권한**: 주문자 본인 또는 `MASTER`",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 누락, UUID 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문자 본인도 MASTER 도 아님 (ORDER_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 주문 (ORDER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") String role,
            @Parameter(description = "주문 ID") UUID orderId);

    @Operation(
            summary = "주문 목록 조회",
            description = """
                    **조회 범위**
                    - `MASTER`: 삭제되지 않은 전체 주문
                    - 그 외: 본인이 생성한 주문 (`FAILED` 포함)

                    **페이징**: `page` 0 이상(기본 0), `size` 1~100(기본 10). `createdAt` 내림차순 정렬
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "page·size 범위 오류, 필수 헤더 누락, 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getOrders(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") String role,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "10")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하이어야 합니다.") int size);

    @Operation(
            summary = "받은 주문 단건 조회",
            description = """
                    공급 업체 입장에서 받은 주문을 조회합니다. `FAILED` 주문도 조회됩니다.

                    **권한**: 주문의 공급 업체 담당자(`Company.userId == X-User-Id`) 또는 `MASTER`
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 누락, UUID 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "공급 업체 담당자도 MASTER 도 아님 (RECEIVED_ORDER_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 주문 (ORDER_NOT_FOUND), 공급 업체 없음 (SUPPLIER_COMPANY_NOT_FOUND, MASTER 가 아닐 때만 조회)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "공급 업체 조회 중 404 외의 호출 실패 (company-service 장애, MASTER 가 아닐 때만 호출)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<OrderResponse>> getReceivedOrder(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") String role,
            @Parameter(description = "주문 ID") UUID orderId);

    @Operation(
            summary = "받은 주문 목록 조회",
            description = """
                    **조회 범위**
                    - `MASTER`: 삭제되지 않은 전체 주문 (`FAILED` 포함)
                    - 공급 업체 담당자: 본인 소속 업체(`X-User-Affiliation-Id`)가 공급 업체인 주문 (`FAILED` 제외)

                    **권한**: `MASTER`, 또는 `COMPANY` 소속이면서 소속 업체의 담당자(`Company.userId == X-User-Id`)

                    **페이징**: `page` 0 이상(기본 0), `size` 1~100(기본 10). `createdAt` 내림차순 정렬
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "page·size 범위 오류, 필수 헤더 누락, 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "COMPANY 소속이 아니거나 소속 업체 담당자가 아님 (RECEIVED_ORDER_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "소속 업체 없음 (AFFILIATION_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "소속 업체 조회 중 404 외의 호출 실패 (company-service 장애)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getReceivedOrders(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") String role,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Affiliation-Type", required = true, description = "요청 사용자 소속 유형 (MASTER 가 아니면 COMPANY 여야 함)", example = "COMPANY") String userAffiliationType,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Affiliation-Id", required = true, description = "요청 사용자 소속 업체 ID") UUID userAffiliationId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "10")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하이어야 합니다.") int size);

    @Operation(
            summary = "주문 수정",
            description = """
                    수량, 요청사항, 수령인을 수정합니다.
                    1. 수량이 바뀌면 공급 업체 허브의 재고를 차이만큼 조정합니다.
                    2. 배송의 수령인을 변경합니다. 실패하면 조정한 재고를 되돌리고 오류로 응답합니다.

                    **권한**: 주문자 본인 또는 `MASTER`. 주문이 `CREATED` 이고 배송이 `HUB_WAITING` 일 때만 가능합니다.
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·검증 실패, 필수 헤더 누락, UUID·본문 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문자 본인도 MASTER 도 아님 (ORDER_MODIFY_DENIED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 주문 (ORDER_NOT_FOUND), 배송 정보 없음 (DELIVERY_NOT_FOUND), 공급 업체 없음 (SUPPLIER_COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CREATED 가 아닌 주문 (ORDER_NOT_MODIFIABLE), 배송이 이미 시작됨 (ORDER_DELIVERY_STARTED), 늘린 수량만큼 재고 부족 (OUT_OF_STOCK_FOR_UPDATE), "
                            + "재고가 삭제된 상품 (PRODUCT_DISCONTINUED), 재고가 등록되지 않은 상품 (PRODUCT_NOT_READY)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "배송 조회 실패 (DELIVERY_FETCH_FAILED), 재고 조정 중 오류 (STOCK_ADJUST_FAILED), 배송 수령인 변경 실패 (DELIVERY_UPDATE_FAILED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "공급 업체 조회 중 404 외의 호출 실패 (company-service 장애)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") String role,
            @Parameter(description = "주문 ID") UUID orderId,
            @Valid UpdateOrderRequest request);

    @Operation(
            summary = "주문 취소",
            description = """
                    주문을 `CANCELED` 로 바꾸고 논리 삭제합니다.
                    - `CREATED` 주문: 배송이 `HUB_WAITING`(또는 이미 `CANCELED`) 인지 확인 후 배송을 취소하고 재고를 복구합니다.
                      (재고 복구 실패는 기록만 남기고 취소는 진행. 이미 복구된 재고는 다시 복구하지 않음)
                    - `FAILED` 주문: 재고·배송 처리 없이 삭제만 합니다.

                    **권한**: 주문자 본인 또는 `MASTER`
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 누락, UUID 형식 오류",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문자 본인도 MASTER 도 아님 (ORDER_DELETE_DENIED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 주문 (ORDER_NOT_FOUND), 배송 정보 없음 (DELIVERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "배송이 이미 시작됨 — HUB_WAITING·CANCELED 가 아님 (ORDER_DELIVERY_STARTED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "배송 조회 실패 (DELIVERY_FETCH_FAILED), 배송 취소 실패 (DELIVERY_DELETE_FAILED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> deleteOrder(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") String role,
            @Parameter(description = "주문 ID") UUID orderId);
}
