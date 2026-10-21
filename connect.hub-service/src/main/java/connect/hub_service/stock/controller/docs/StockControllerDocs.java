package connect.hub_service.stock.controller.docs;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.stock.dto.StockCreateRequest;
import connect.hub_service.stock.dto.StockInboundHistoryResponse;
import connect.hub_service.stock.dto.StockInboundRequest;
import connect.hub_service.stock.dto.StockInboundResponse;
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
@Tag(name = "Stock", description = "허브별 상품 재고 API (등록·입고·입고 이력 조회는 X-User-Id / X-User-Role 헤더 필요)")
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
                    요청 본문의 `hubId`·`productId` 로 기존 재고를 찾아 수량을 더하고, 입고 이력을 남깁니다.

                    - `inboundId` 는 입고 한 건의 멱등 키입니다. 입고마다 새로 만들고, 응답을 받지 못해 재시도할 때는 **같은 값**을 보냅니다.
                    - 같은 `inboundId` 의 같은 요청(허브·상품·수량)이 다시 오면 수량을 더하지 않고 `alreadyProcessed=true` 로 최초 결과를 반환합니다.
                    - 같은 `inboundId` 인데 허브·상품·수량이 다르면 409 입니다.
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "삭제된 재고(STOCK_DELETED), 업체의 소속 허브가 아님(STOCK_HUB_MISMATCH), 수량 범위 초과(STOCK_QUANTITY_OVERFLOW), "
                            + "같은 입고 ID 의 다른 요청(STOCK_INBOUND_CONFLICT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "company-service 호출 실패 (COMPANY_SERVICE_UNAVAILABLE)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockInboundResponse> inbound(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "MASTER") String role,
            StockInboundRequest request);

    @Operation(
            summary = "재고 입고 이력 조회",
            description = """
                    재고별 입고 이력을 최신순으로 조회합니다.

                    - MASTER 는 company-service 를 호출하지 않고 조회하며, 삭제된 재고의 이력도 조회할 수 있습니다. `companyId` 는 생략할 수 있습니다.
                    - 업체 관리자는 `companyId` 가 필요하고, 재고의 허브·상품으로 재고 등록과 같은 순서로 확인합니다: 업체 존재 → 권한 → 허브가 업체의 소속 허브인지 → 상품 존재
                    - 업체 관리자는 삭제된 재고의 이력을 조회할 수 없습니다 (409).

                    **페이징**: `page` 는 0 이상, `size` 는 1~100

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "페이지 번호·크기 범위 오류(INVALID_PAGE), UUID 형식 오류(INVALID_INPUT), 필수 헤더 누락(MISSING_HEADER), "
                            + "MASTER 가 아닌데 companyId 누락(COMPANY_ID_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (FORBIDDEN)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 재고(STOCK_NOT_FOUND), 없는 업체(COMPANY_NOT_FOUND), 업체에 없는 상품(PRODUCT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "업체 관리자의 삭제된 재고 조회(STOCK_DELETED), 업체의 소속 허브가 아님(STOCK_HUB_MISMATCH)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "company-service 호출 실패 (COMPANY_SERVICE_UNAVAILABLE)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<PagedResponse<StockInboundHistoryResponse>> getInbounds(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "요청 사용자 ID") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true, description = "요청 사용자 권한", example = "MASTER") String role,
            @Parameter(description = "재고 ID") UUID stockId,
            @Parameter(description = "업체 ID (MASTER 가 아니면 필수, 권한 확인용)") UUID companyId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "10") int size);

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
