package connect.hub_service.stock.controller.docs;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.stock.dto.StockAdjustRequest;
import connect.hub_service.stock.dto.StockAdjustResponse;
import connect.hub_service.stock.dto.StockDecreaseRequest;
import connect.hub_service.stock.dto.StockDecreaseResponse;
import connect.hub_service.stock.dto.StockDeleteRequest;
import connect.hub_service.stock.dto.StockDeleteResponse;
import connect.hub_service.stock.dto.StockHistoryResponse;
import connect.hub_service.stock.dto.StockRestoreRequest;
import connect.hub_service.stock.dto.StockRestoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

// InternalStockController 의 Swagger 문서 (동작은 InternalStockController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Internal Stock (내부 전용)", description = """
        **서비스 간 내부 호출 전용 API** (호출: order-service — 차감·조정·복원, company-service — 삭제, Feign)
        - Gateway 라우트에 없어 외부 클라이언트는 호출할 수 없습니다.
        - 권한 검사를 하지 않습니다. 삭제만 감사용 `X-User-Id` 헤더를 받습니다.
        """)
public interface InternalStockControllerDocs {

    @Operation(
            summary = "[내부] 주문 재고 차감",
            description = """
                    주문 생성 시 order-service 가 호출합니다. 재고 차감과 DECREASE 이력 저장은 한 트랜잭션입니다.

                    - `orderId` 기준으로 멱등합니다. 같은 주문의 같은 요청(허브·상품·수량)이 다시 오면 차감하지 않고 `alreadyProcessed=true` 로 기존 결과를 반환합니다.
                    - 같은 `orderId` 인데 허브·상품·수량이 다르면 409 입니다.
                    - 이미 복원된 주문은 다시 차감할 수 없습니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "차감 성공 (또는 이미 처리된 요청)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류 (VALIDATION_FAILED, INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "등록되지 않은 재고 (STOCK_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "재고 부족(INSUFFICIENT_STOCK), 삭제된 재고(STOCK_DELETED), 이미 복원된 주문(STOCK_ALREADY_RESTORED), 같은 주문 ID 의 다른 요청(STOCK_REQUEST_CONFLICT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockDecreaseResponse> decrease(StockDecreaseRequest request);

    @Operation(
            summary = "[내부] 주문 수량 수정에 따른 재고 조정",
            description = """
                    주문 수량 수정 시 order-service 가 호출합니다.

                    - `quantityDelta` = 수정 후 주문 수량 - 수정 전 주문 수량 (양수: 추가 차감, 음수: 재고 반환, 0 은 400)
                    - 대상 재고는 해당 주문의 최초 차감(DECREASE) 이력의 재고이며, 요청의 허브·상품과 같아야 합니다.
                    - `adjustmentId` 기준으로 멱등합니다. 같은 조정이 다시 오면 재고를 바꾸지 않고 `alreadyProcessed=true` 로 응답합니다.
                    - 조정 후 주문이 점유하는 수량은 1 이상이어야 하고, 복원된 주문은 조정할 수 없습니다.
                    - 삭제된 재고에는 추가 차감(양수)은 불가, 재고 반환(음수)은 허용합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조정 성공 (또는 이미 처리된 요청)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED, INVALID_INPUT), 증감량 0(INVALID_QUANTITY_DELTA)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "재고 없음 (STOCK_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "차감 이력 없음(DECREASE_HISTORY_NOT_FOUND), 차감 이력의 허브·상품과 다름(STOCK_HISTORY_MISMATCH), 같은 조정 ID 의 다른 요청(STOCK_ADJUSTMENT_CONFLICT), "
                            + "이미 복원된 주문(STOCK_ALREADY_RESTORED), 조정 후 점유 수량 1 미만(STOCK_ALLOCATION_UNDERFLOW), 재고 부족(INSUFFICIENT_STOCK), 삭제된 재고에 추가 차감(STOCK_DELETED), 수량 범위 초과(STOCK_QUANTITY_OVERFLOW)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockAdjustResponse> adjust(StockAdjustRequest request);

    @Operation(
            summary = "[내부] 주문 취소에 따른 재고 복원",
            description = """
                    주문 취소 시 order-service 가 호출합니다.

                    - 복원 수량은 요청 값이 아니라 이력 기준 현재 점유 수량(최초 차감 + 모든 조정)입니다.
                    - `orderId` 기준으로 멱등합니다. 이미 복원된 주문이면 재고를 바꾸지 않고 `alreadyProcessed=true` 로 응답합니다.
                    - 재고가 삭제된 뒤에도 복원하며, 삭제 상태는 유지합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복원 성공 (또는 이미 복원됨)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류 (VALIDATION_FAILED, INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "재고 없음 (STOCK_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "차감 이력 없음(DECREASE_HISTORY_NOT_FOUND), 차감 이력의 허브·상품과 다름(STOCK_HISTORY_MISMATCH), 점유 수량 1 미만(STOCK_ALLOCATION_UNDERFLOW), 수량 범위 초과(STOCK_QUANTITY_OVERFLOW)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockRestoreResponse> restore(StockRestoreRequest request);

    @Operation(
            summary = "[내부] 상품 삭제에 따른 재고 삭제",
            description = """
                    상품 삭제 시 company-service 가 호출합니다.

                    - 요청한 상품들의 모든 허브 재고를 논리 삭제합니다. 수량은 유지하고 삭제 시각·삭제자(`X-User-Id`)만 기록합니다.
                    - 이미 삭제된 재고는 대상에서 빠지므로, 같은 요청을 다시 보내도 `deletedCount=0` 으로 성공합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공 (data.deletedCount 에 삭제한 재고 수)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED, INVALID_INPUT), 필수 헤더 누락(MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockDeleteResponse> deleteByProductIds(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true, description = "삭제 작업을 수행한 사용자 ID (deletedBy 로 기록)") UUID userId,
            StockDeleteRequest request);

    @Operation(
            summary = "[내부] 주문의 재고 처리 이력 조회",
            description = "주문의 차감(DECREASE)·조정(ADJUST)·복원(RESTORE) 이력을 처리 순서대로 조회합니다. 이력이 없으면 빈 목록입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "orderId 형식 오류(INVALID_INPUT), orderId 누락(UNSUPPORTED_REQUEST)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<StockHistoryResponse> getHistories(@Parameter(description = "주문 ID", required = true) UUID orderId);
}
