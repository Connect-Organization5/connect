package connect.hub_service.stock.controller;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.stock.dto.StockDecreaseRequest;
import connect.hub_service.stock.dto.StockDecreaseResponse;
import connect.hub_service.stock.dto.StockDeleteRequest;
import connect.hub_service.stock.dto.StockDeleteResponse;
import connect.hub_service.stock.dto.StockHistoryResponse;
import connect.hub_service.stock.dto.StockRestoreRequest;
import connect.hub_service.stock.dto.StockRestoreResponse;
import connect.hub_service.stock.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 서비스 간 호출 전용(Order → Hub). Gateway 라우팅에 포함하지 않으며, 신뢰 경계는 내부 네트워크다.
// 차감·복원·이력 조회는 X-User-Id, X-User-Role에 의존하지 않고, 삭제만 감사용 X-User-Id를 받는다.
@RestController
@RequestMapping("/internal/hubs/stocks")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockService stockService;

    @PostMapping("/decrease")
    public ApiResponse<StockDecreaseResponse> decrease(@Valid @RequestBody StockDecreaseRequest request) {
        return ApiResponse.ok("재고 차감 성공", stockService.decrease(request));
    }

    @PostMapping("/restore")
    public ApiResponse<StockRestoreResponse> restore(@Valid @RequestBody StockRestoreRequest request) {
        return ApiResponse.ok("재고 복원 성공", stockService.restore(request));
    }

    // 삭제 작업을 수행한 사용자 ID(X-User-Id)는 deleted_by에 기록한다. 누락은 400(MISSING_HEADER), UUID 형식 오류는 400.
    // 차감·복원·이력 조회는 이 헤더를 요구하지 않는다.
    @PostMapping("/delete")
    public ApiResponse<StockDeleteResponse> deleteByProductIds(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody StockDeleteRequest request) {
        return ApiResponse.ok("재고 삭제 성공", stockService.deleteByProductIds(userId, request));
    }

    @GetMapping("/histories")
    public ApiResponse<StockHistoryResponse> getHistories(@RequestParam UUID orderId) {
        return ApiResponse.ok("재고 처리 이력 조회 성공", stockService.getHistories(orderId));
    }
}
