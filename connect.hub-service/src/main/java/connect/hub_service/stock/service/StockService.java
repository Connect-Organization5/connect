package connect.hub_service.stock.service;

import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import connect.hub_service.global.util.PageRequests;
import connect.hub_service.hub.entity.Hub;
import connect.hub_service.hub.repository.HubRepository;
import connect.hub_service.stock.dto.StockCreateRequest;
import connect.hub_service.stock.dto.StockInboundRequest;
import connect.hub_service.stock.dto.StockResponse;
import connect.hub_service.stock.entity.Stock;
import connect.hub_service.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    // Gateway가 검증해 전달한 X-User-Role 값. 재고 등록은 MASTER만 허용(잠정: SA 권한 매트릭스 기준,
    // 업체 관리자의 본인 업체 상품 등록은 Company 연동 후 검토).
    static final String MASTER = "MASTER";

    private static final Sort DEFAULT_SORT = Sort.by("createdAt").and(Sort.by("id"));

    private final StockRepository stockRepository;
    private final HubRepository hubRepository;

    @Transactional
    public StockResponse register(String role, StockCreateRequest request) {
        if (!MASTER.equals(role)) {
            throw new BaseException(ErrorCode.FORBIDDEN);
        }

        // 잠정: 상품 존재 검증(Company 연동)은 API 확정 후 추가한다. 현재는 UUID 형식만 확인한다.
        Hub hub = hubRepository.findById(request.hubId())
                .orElseThrow(() -> new BaseException(ErrorCode.HUB_NOT_FOUND));

        // 같은 재고가 있으면 수량을 덮어쓰거나 합산하지 않고 충돌 처리한다. (삭제된 재고 포함)
        if (stockRepository.existsByHubIdAndProductId(hub.getId(), request.productId())) {
            throw new BaseException(ErrorCode.STOCK_ALREADY_EXISTS);
        }

        try {
            Stock saved = stockRepository.saveAndFlush(new Stock(hub, request.productId(), request.quantity()));
            return StockResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 (허브, 상품)이 등록된 경우 UNIQUE 제약이 막는다.
            throw new BaseException(ErrorCode.STOCK_ALREADY_EXISTS);
        }
    }

    /**
     * 기존 재고의 수량을 늘린다(입고). 재고 행을 잠근 뒤 더하므로 동시 입고의 증가분이 유실되지 않는다.
     * 멱등하지 않다: 응답 유실 후 같은 요청을 다시 보내면 수량이 중복 증가한다. 입고 이력은 남기지 않는다.
     */
    @Transactional
    public StockResponse inbound(String role, StockInboundRequest request) {
        if (!MASTER.equals(role)) {
            throw new BaseException(ErrorCode.FORBIDDEN);
        }

        if (!hubRepository.existsById(request.hubId())) {
            throw new BaseException(ErrorCode.HUB_NOT_FOUND);
        }

        // 삭제된 재고도 조회해 없음(404)과 삭제됨(409)을 구분한다.
        Stock stock = stockRepository.findByHubIdAndProductIdForUpdate(request.hubId(), request.productId())
                .orElseThrow(() -> new BaseException(ErrorCode.STOCK_NOT_FOUND));
        if (stock.getDeletedAt() != null) {
            throw new BaseException(ErrorCode.STOCK_DELETED);
        }

        try {
            stock.increase(request.quantity());
        } catch (ArithmeticException e) {
            throw new BaseException(ErrorCode.STOCK_QUANTITY_OVERFLOW);
        }
        return StockResponse.from(stock);
    }

    public PagedResponse<StockResponse> getStocks(UUID hubId, UUID productId, int page, int size) {
        PageRequest pageRequest = PageRequests.of(page, size, DEFAULT_SORT);

        Page<Stock> stocks;
        if (hubId != null && productId != null) {
            stocks = stockRepository.findByHubIdAndProductIdAndDeletedAtIsNull(hubId, productId, pageRequest);
        } else if (hubId != null) {
            stocks = stockRepository.findByHubIdAndDeletedAtIsNull(hubId, pageRequest);
        } else if (productId != null) {
            stocks = stockRepository.findByProductIdAndDeletedAtIsNull(productId, pageRequest);
        } else {
            stocks = stockRepository.findByDeletedAtIsNull(pageRequest);
        }
        return PagedResponse.from(stocks.map(StockResponse::from));
    }

    public StockResponse getStock(UUID stockId) {
        Stock stock = stockRepository.findByIdAndDeletedAtIsNull(stockId)
                .orElseThrow(() -> new BaseException(ErrorCode.STOCK_NOT_FOUND));
        return StockResponse.from(stock);
    }
}
