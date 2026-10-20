package connect.delivery_service.domain.controller.docs;

import connect.delivery_service.client.user.AffiliationType;
import connect.delivery_service.client.user.RoleType;
import connect.delivery_service.domain.dto.ChangeDeliveryUserRequest;
import connect.delivery_service.domain.dto.ChangeReceiverRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.domain.dto.DeliveryRouteResponse;
import connect.delivery_service.domain.dto.DeliverySearchCondition;
import connect.delivery_service.global.dto.ApiResponse;
import connect.delivery_service.global.dto.ErrorResponse;
import connect.delivery_service.global.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static connect.delivery_service.global.config.OpenApiConfig.GATEWAY_AUTH;

// DeliveryController 의 Swagger 문서 (동작은 DeliveryController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Delivery", description = "배송 관리 API (Gateway 경유, X-User-* 헤더 필요)")
public interface DeliveryControllerDocs {

    @Operation(
            summary = "배송 단건 조회",
            description = "**권한**: 주문자, 배송 담당자, `MASTER`. 그 외에는 404 로 응답합니다.",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 누락(MISSING_HEADER), UUID·Role 형식 오류(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송이거나 조회 권한 없음 (DELIVERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<DeliveryResponse>> getDelivery(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") RoleType role);

    @Operation(
            summary = "배송 목록 조회·검색",
            description = """
                    **조회 범위**
                    - `HUB` 소속: 본인이 담당하는 배송만 (배송 또는 배송 경로의 담당자가 본인)
                    - `COMPANY` 소속: 조건 없이 전체

                    **페이징**: `size` 는 10, 30, 50 만 허용 (그 외 값은 10). `sort` 는 `createdAt` 또는 `updatedAt` + `,asc|desc` (기본 `createdAt,desc`)
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 누락(MISSING_HEADER), 형식 오류(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<PagedResponse<DeliveryResponse>>> searchDeliveries(
            @ParameterObject DeliverySearchCondition cond,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Affiliation-Type", required = true, description = "요청 사용자 소속", example = "HUB") AffiliationType affiliationType);

    @Operation(
            summary = "허브 출발",
            description = """
                    `HUB_WAITING` → `HUB_MOVING`. 배송 경로도 `WAITING` → `MOVING` 으로 바뀝니다.

                    **권한**: 배송 담당자 본인만. 담당자가 배정되지 않은 배송은 출발할 수 없습니다.
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송이거나 담당자가 아님 (DELIVERY_NOT_FOUND), 배송 경로 없음 (DELIVERY_ROUTE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 처리 불가 (INVALID_DELIVERY_STATUS), 담당자 미배정 (DELIVERY_USER_NOT_ASSIGNED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> startHubMoving(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "배송 담당자 ID") UUID userId);

    @Operation(
            summary = "목적지 허브 도착",
            description = """
                    `HUB_MOVING` → `DEST_HUB_ARRIVED`. 배송 경로는 `ARRIVED` 가 되고 실제 거리·소요시간이 기록됩니다.

                    **권한**: 배송 담당자 본인만
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송이거나 담당자가 아님 (DELIVERY_NOT_FOUND), 배송 경로 없음 (DELIVERY_ROUTE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 처리 불가 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> arriveAtDestinationHub(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "배송 담당자 ID") UUID userId);

    @Operation(
            summary = "업체 배송 출발",
            description = "`DEST_HUB_ARRIVED` → `OUT_FOR_DELIVERY`.\n\n**권한**: 배송 담당자 본인만",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송이거나 담당자가 아님 (DELIVERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 처리 불가 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> startDelivery(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "배송 담당자 ID") UUID userId);

    @Operation(
            summary = "배송 완료",
            description = "`OUT_FOR_DELIVERY` → `DELIVERED`.\n\n**권한**: 배송 담당자 본인만",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송이거나 담당자가 아님 (DELIVERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 처리 불가 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> completeDelivery(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "배송 담당자 ID") UUID userId);

    @Operation(
            summary = "배송 담당자 배정 (본인)",
            description = """
                    요청한 사용자 본인을 배송과 배송 경로의 담당자로 배정합니다.
                    시스템 자동 배정에서 미배정으로 남은 배송을 허브 담당자가 직접 가져갈 때 사용합니다.

                    **권한**: `HUB` 소속만. `HUB_WAITING` 상태이고 담당자가 없을 때만 가능합니다.
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "배정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "HUB 소속이 아님 (DELIVERY_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송 (DELIVERY_NOT_FOUND), 배송 경로 없음 (DELIVERY_ROUTE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 담당자 있음 (DELIVERY_USER_ALREADY_ASSIGNED), 현재 상태에서 처리 불가 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<DeliveryResponse>> assignDeliveryUserId(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "배정받을 사용자 ID (본인)") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Affiliation-Type", required = true, description = "요청 사용자 소속", example = "HUB") AffiliationType affiliationType);

    @Operation(
            summary = "배송 담당자 변경",
            description = "배송과 배송 경로의 담당자를 지정한 사용자로 바꿉니다.\n\n**권한**: `MASTER` 만. `HUB_WAITING` 상태에서만 가능합니다.",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "MASTER 가 아님 (DELIVERY_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송 (DELIVERY_NOT_FOUND), 배송 경로 없음 (DELIVERY_ROUTE_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 처리 불가 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<DeliveryResponse>> changeDeliveryUserId(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "MASTER") RoleType role,
            ChangeDeliveryUserRequest request);

    @Operation(
            summary = "수령인 변경",
            description = "**권한**: 주문자, `MASTER`. `HUB_WAITING` 상태에서만 가능합니다.",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송이거나 권한 없음 (DELIVERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 상태에서 처리 불가 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<DeliveryResponse>> changeReceiver(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") RoleType role,
            ChangeReceiverRequest request);

    @Operation(
            summary = "배송 삭제",
            description = "배송과 배송 경로를 논리 삭제합니다.\n\n**권한**: 주문자, `MASTER`. `DELIVERED` 또는 `CANCELED` 상태에서만 가능합니다.",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 배송이거나 권한 없음 (DELIVERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "진행 중인 배송 (INVALID_DELIVERY_STATUS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> deleteDelivery(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") RoleType role);

    @Operation(
            summary = "배송 경로 조회",
            description = "예상·실제 거리(km)와 소요시간(분), 경로 상태를 조회합니다.\n\n**권한**: 배송 경로 담당자, 주문자, `MASTER`",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배송 경로 없음 (DELIVERY_ROUTE_NOT_FOUND), 권한 없음 (DELIVERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<DeliveryRouteResponse>> getDeliveryRoute(
            @Parameter(description = "배송 ID") UUID deliveryId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "USER") RoleType role);
}
