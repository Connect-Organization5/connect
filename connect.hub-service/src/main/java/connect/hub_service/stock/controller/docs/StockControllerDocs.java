package connect.hub_service.stock.controller.docs;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.stock.dto.StockCreateRequest;
import connect.hub_service.stock.dto.StockInboundRequest;
import connect.hub_service.stock.dto.StockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static connect.hub_service.global.config.OpenApiConfig.GATEWAY_AUTH;

// StockController 의 Swagger 문서 (동작은 StockController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Stock", description = "허브별 상품 재고 API (등록·입고는 X-User-Id / X-User-Role 헤더 필요)")
public interface StockControllerDocs {

    @Operation(
            summary = "재고 등록",
            description = """
                    허브에 상품 재고를 새로 등록합니다. 같은 허브·상품 재고가 이미 있으면(삭제된 재고 포함) 수량을 합치지 않고 409 입니다.

                    company-service 에 다음 순서로 확인합니다: 업체 존재 → 권한 → 허브가 업체의 소속 허브인지 → 상품 존재

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED, INVALID_INPUT), 필수 헤더 누락(MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (FORBIDDEN)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 허브(HUB_NOT_FOUND), 없는 업체(COMPANY_NOT_FOUND), 업체에 없는 상품(PRODUCT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 허브·상품 재고(STOCK_ALREADY_EXISTS), 업체의 소속 허브가 아님(STOCK_HUB_MISMATCH)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "company-service 호출 실패 (COMPANY_SERVICE_UNAVAILABLE)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<StockResponse>> register(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "MASTER") String role,
            StockCreateRequest request);

    @Operation(
            summary = "재고 입고 (수량 추가)",
            description = """
                    요청 본문의 `hubId`·`productId` 로 기존 재고를 찾아 수량을 더합니다.

                    - **멱등하지 않습니다.** 응답을 받지 못해 같은 요청을 다시 보내면 수량이 중복으로 늘어납니다.
                    - 입고 이력은 남기지 않습니다.
                    - 권한·업체 확인은 재고 등록과 같습니다.

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "입고 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED, INVALID_INPUT), 필수 헤더 누락(MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (FORBIDDEN)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 허브(HUB_NOT_FOUND), 없는 업체(COMPANY_NOT_FOUND), 업체에 없는 상품(PRODUCT_NOT_FOUND), 등록되지 않은 재고(STOCK_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "삭제된 재고(STOCK_DELETED), 업체의 소속 허브가 아님(STOCK_HUB_MISMATCH), 수량 범위 초과(STOCK_QUANTITY_OVERFLOW)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "company-service 호출 실패 (COMPANY_SERVICE_UNAVAILABLE)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockResponse> inbound(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "MASTER") String role,
            StockInboundRequest request);

    @Operation(
            summary = "재고 목록 조회",
            description = """
                    삭제되지 않은 재고를 조회합니다. `hubId`, `productId` 는 각각 선택이며 둘 다 있으면 AND 조건입니다. 등록 순으로 정렬합니다.

                    **페이징**: `page` 는 0 이상, `size` 는 1~100

                    **권한**: 별도 권한 검사 없음
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "페이지 번호·크기 범위 오류(INVALID_PAGE), UUID 형식 오류(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<PagedResponse<StockResponse>> getStocks(
            @Parameter(description = "허브 ID (생략 시 조건 없음)") UUID hubId,
            @Parameter(description = "상품 ID (생략 시 조건 없음)") UUID productId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "10") int size);

    @Operation(
            summary = "재고 단건 조회",
            description = "삭제되지 않은 재고만 조회합니다.\n\n**권한**: 별도 권한 검사 없음",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 재고 (STOCK_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockResponse> getStock(@Parameter(description = "재고 ID") UUID stockId);
}
