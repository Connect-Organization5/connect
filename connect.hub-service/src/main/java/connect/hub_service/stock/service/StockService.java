package connect.hub_service.stock.service;

import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import connect.hub_service.global.util.PageRequests;
import connect.hub_service.hub.entity.Hub;
import connect.hub_service.hub.repository.HubRepository;
import connect.hub_service.stock.dto.StockCreateRequest;
import connect.hub_service.stock.dto.StockDecreaseRequest;
import connect.hub_service.stock.dto.StockDecreaseResponse;
import connect.hub_service.stock.dto.StockDeleteRequest;
import connect.hub_service.stock.dto.StockDeleteResponse;
import connect.hub_service.stock.dto.StockHistoryResponse;
import connect.hub_service.stock.dto.StockInboundRequest;
import connect.hub_service.stock.dto.StockResponse;
import connect.hub_service.stock.dto.StockRestoreRequest;
import connect.hub_service.stock.dto.StockRestoreResponse;
import connect.hub_service.stock.entity.Stock;
import connect.hub_service.stock.entity.StockHistory;
import connect.hub_service.stock.entity.StockHistoryType;
import connect.hub_service.stock.repository.StockHistoryRepository;
import connect.hub_service.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
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
    private final StockHistoryRepository stockHistoryRepository;
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

    /**
     * 주문 재고 차감. orderId 기준으로 멱등하다(Order는 주문당 단일 상품만 보낸다).
     * 재고 변경과 DECREASE 이력은 한 트랜잭션이며, 같은 orderId를 다른 재고로 동시에 요청하면 UNIQUE(order_id, type)이 막는다.
     */
    @Transactional
    public StockDecreaseResponse decrease(StockDecreaseRequest request) {
        UUID orderId = request.orderId();
        int quantity = request.quantity();

        StockDecreaseResponse processed = findProcessedDecrease(request);
        if (processed != null) {
            return processed;
        }

        // 재고 행을 잠근 뒤 같은 주문의 이력을 같은 규칙으로 다시 확인한다. 잠금 전에 다른 요청이 커밋됐을 수 있다.
        Stock stock = stockRepository.findByHubIdAndProductIdForUpdate(request.hubId(), request.productId())
                .orElseThrow(() -> new BaseException(ErrorCode.STOCK_NOT_FOUND));
        processed = findProcessedDecrease(request);
        if (processed != null) {
            return processed;
        }

        if (stock.getDeletedAt() != null) {
            throw new BaseException(ErrorCode.STOCK_DELETED);
        }
        if (stock.getQuantity() < quantity) {
            throw new BaseException(ErrorCode.INSUFFICIENT_STOCK);
        }

        stock.decrease(quantity);
        saveHistory(StockHistory.decrease(orderId, stock, quantity));
        return new StockDecreaseResponse(orderId, stock.getId(), quantity, stock.getQuantity(), false);
    }

    /**
     * 주문 재고 복원. 수량은 원본 DECREASE 이력에서 가져오고 별도의 RESTORE 이력을 추가한다.
     * 재고가 논리 삭제된 뒤에도 복원하며, 삭제 상태(deletedAt·deletedBy)는 그대로 유지한다.
     */
    @Transactional
    public StockRestoreResponse restore(StockRestoreRequest request) {
        UUID orderId = request.orderId();

        StockHistory decrease = findHistory(orderId, StockHistoryType.DECREASE);
        if (decrease == null) {
            throw new BaseException(ErrorCode.DECREASE_HISTORY_NOT_FOUND);
        }
        Stock original = decrease.getStock();
        if (!original.getHub().getId().equals(request.hubId()) || !original.getProductId().equals(request.productId())) {
            throw new BaseException(ErrorCode.STOCK_HISTORY_MISMATCH);
        }

        StockHistory restore = findHistory(orderId, StockHistoryType.RESTORE);
        if (restore != null) {
            return replayRestore(restore, original);
        }

        // 원본 이력의 재고를 잠근 뒤 RESTORE 이력을 다시 확인한다. 삭제된 재고도 대상이다.
        Stock stock = stockRepository.findByIdForUpdate(original.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.STOCK_NOT_FOUND));
        restore = findHistory(orderId, StockHistoryType.RESTORE);
        if (restore != null) {
            return replayRestore(restore, stock);
        }

        int quantity = decrease.getQuantity();
        try {
            stock.increase(quantity);
        } catch (ArithmeticException e) {
            throw new BaseException(ErrorCode.STOCK_QUANTITY_OVERFLOW);
        }
        saveHistory(StockHistory.restore(orderId, stock, quantity));
        return new StockRestoreResponse(orderId, stock.getId(), quantity, stock.getQuantity(), false);
    }

    /**
     * 상품들의 모든 허브 활성 재고를 논리 삭제한다. 수량은 유지하고 삭제 시각·삭제자(userId)만 기록한다.
     * 전체가 하나의 로컬 트랜잭션이며, 이미 삭제된 재고는 대상에서 빠지므로 같은 요청을 다시 보내도 deletedCount=0으로 성공한다.
     * HeaderAuditorAware는 deletedBy를 채우지 않으므로 userId를 명시적으로 전달한다.
     */
    @Transactional
    public StockDeleteResponse deleteByProductIds(UUID userId, StockDeleteRequest request) {
        LocalDateTime deletedAt = LocalDateTime.now();
        Set<UUID> productIds = new HashSet<>(request.productIds());

        int deletedCount = 0;
        for (Stock stock : stockRepository.findActiveByProductIdsForUpdate(productIds)) {
            if (stock.delete(userId, deletedAt)) {
                deletedCount++;
            }
        }
        return new StockDeleteResponse(deletedCount);
    }

    public StockHistoryResponse getHistories(UUID orderId) {
        return StockHistoryResponse.of(orderId, stockHistoryRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId));
    }

    private StockHistory findHistory(UUID orderId, StockHistoryType type) {
        return stockHistoryRepository.findByOrderIdAndType(orderId, type).orElse(null);
    }

    // 이미 처리된 주문의 차감 요청을 판정한다. 처리 이력이 없으면 null을 반환해 새로 차감하도록 한다.
    // 1) 복원 완료 주문은 요청 값과 무관하게 409, 2) 차감 이력이 있으면 같은 요청은 처리 결과 반환·다르면 409.
    private StockDecreaseResponse findProcessedDecrease(StockDecreaseRequest request) {
        UUID orderId = request.orderId();
        if (findHistory(orderId, StockHistoryType.RESTORE) != null) {
            throw new BaseException(ErrorCode.STOCK_ALREADY_RESTORED);
        }
        StockHistory decrease = findHistory(orderId, StockHistoryType.DECREASE);
        if (decrease == null) {
            return null;
        }

        Stock stock = decrease.getStock();
        boolean same = stock.getHub().getId().equals(request.hubId())
                && stock.getProductId().equals(request.productId())
                && decrease.getQuantity().equals(request.quantity());
        if (!same) {
            throw new BaseException(ErrorCode.STOCK_REQUEST_CONFLICT);
        }
        return new StockDecreaseResponse(orderId, stock.getId(), decrease.getQuantity(), stock.getQuantity(), true);
    }

    private StockRestoreResponse replayRestore(StockHistory restore, Stock stock) {
        return new StockRestoreResponse(restore.getOrderId(), stock.getId(), restore.getQuantity(),
                stock.getQuantity(), true);
    }

    // UNIQUE(order_id, type) 위반은 같은 주문 ID로 다른 재고를 동시에 처리한 경우다. 예외로 수량 변경도 함께 롤백된다.
    private void saveHistory(StockHistory history) {
        try {
            stockHistoryRepository.saveAndFlush(history);
        } catch (DataIntegrityViolationException e) {
            throw new BaseException(ErrorCode.STOCK_REQUEST_CONFLICT);
        }
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
