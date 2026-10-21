package connect.hub_service.stock.controller;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.stock.controller.docs.StockControllerDocs;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.stock.dto.StockCreateRequest;
import connect.hub_service.stock.dto.StockInboundHistoryResponse;
import connect.hub_service.stock.dto.StockInboundRequest;
import connect.hub_service.stock.dto.StockInboundResponse;
import connect.hub_service.stock.dto.StockResponse;
import connect.hub_service.stock.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/hubs/stocks")
@RequiredArgsConstructor
public class StockController implements StockControllerDocs {

    private final StockService stockService;

    // X-User-Id·X-User-Role은 Gateway가 JWT 검증 후 재설정한 값을 신뢰한다. 없으면 400(MISSING_HEADER).
    // MASTER 또는 해당 업체의 관리자만 등록할 수 있다.
    @PostMapping
    public ResponseEntity<ApiResponse<StockResponse>> register(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody StockCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("재고 등록 성공", stockService.register(userId, role, request)));
    }

    // 재고 입고(수량 추가). 요청 본문의 hubId·productId로 기존 재고를 찾는다. MASTER 또는 해당 업체의 관리자만 호출할 수 있다.
    // inboundId로 멱등하다: 같은 inboundId 재요청은 수량을 다시 더하지 않고 최초 결과(alreadyProcessed=true)를 돌려준다.
    @PostMapping("/inbound")
    public ApiResponse<StockInboundResponse> inbound(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody StockInboundRequest request) {
        return ApiResponse.ok("재고 입고 성공", stockService.inbound(userId, role, request));
    }

    // 재고별 입고 이력(최신순). 재고 조회와 같이 로그인 사용자 누구나 조회할 수 있다.
    @GetMapping("/{stockId}/inbounds")
    public ApiResponse<PagedResponse<StockInboundHistoryResponse>> getInbounds(
            @PathVariable UUID stockId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok("재고 입고 이력 조회 성공", stockService.getInbounds(stockId, page, size));
    }

    @GetMapping
    public ApiResponse<PagedResponse<StockResponse>> getStocks(
            @RequestParam(required = false) UUID hubId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok("재고 목록 조회 성공", stockService.getStocks(hubId, productId, page, size));
    }

    @GetMapping("/{stockId}")
    public ApiResponse<StockResponse> getStock(@PathVariable UUID stockId) {
        return ApiResponse.ok("재고 조회 성공", stockService.getStock(stockId));
    }
}
