package connect.hub_service.hub.controller.docs;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.hub.dto.HubRouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

import static connect.hub_service.global.config.OpenApiConfig.GATEWAY_AUTH;

// HubRouteController 의 Swagger 문서 (동작은 HubRouteController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Hub Route", description = "허브 간 이동 경로 조회 API")
public interface HubRouteControllerDocs {

    @Operation(
            summary = "이동 경로 목록 조회",
            description = """
                    출발·도착 허브 조건은 각각 선택이며, 둘 다 있으면 AND 조건입니다. 출발 → 도착 허브 ID 순으로 정렬합니다.

                    **페이징**: `page` 는 0 이상, `size` 는 1~100

                    **권한**: 별도 권한 검사 없음
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "페이지 번호·크기 범위 오류(INVALID_PAGE), UUID 형식 오류(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<PagedResponse<HubRouteResponse>> getRoutes(
            @Parameter(description = "출발 허브 ID (생략 시 조건 없음)") UUID departureHubId,
            @Parameter(description = "도착 허브 ID (생략 시 조건 없음)") UUID arrivalHubId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "10") int size);

    @Operation(
            summary = "출발 → 도착 이동 경로 조회",
            description = """
                    두 허브 간 예상 거리·소요시간을 조회합니다. delivery-service 가 배송 생성 시 호출합니다.

                    - 출발·도착 허브가 같으면(같은 허브 소속 업체 간 주문) 허브 존재만 확인하고 거리·시간 0 을 반환합니다.

                    **권한**: 별도 권한 검사 없음
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "등록된 경로 없음 (ROUTE_NOT_FOUND), 출발·도착이 같은데 없는 허브 (HUB_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<HubRouteResponse> getRoute(
            @Parameter(description = "출발 허브 ID") UUID departureHubId,
            @Parameter(description = "도착 허브 ID") UUID arrivalHubId);
}
