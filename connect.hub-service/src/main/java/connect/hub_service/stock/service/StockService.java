package connect.hub_service.stock.service;

import connect.hub_service.client.company.CompanyValidator;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import connect.hub_service.global.util.PageRequests;
import connect.hub_service.hub.entity.Hub;
import connect.hub_service.hub.repository.HubRepository;
import connect.hub_service.stock.dto.StockAdjustRequest;
import connect.hub_service.stock.dto.StockAdjustResponse;
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
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private static final Sort DEFAULT_SORT = Sort.by("createdAt").and(Sort.by("id"));

    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final HubRepository hubRepository;
    private final EntityManager entityManager;
    private final CompanyValidator companyValidator;

    // 재고 등록은 MASTER 또는 해당 업체의 관리자만 할 수 있다(권한 판단은 CompanyValidator).
    @Transactional
    public StockResponse register(UUID userId, String role, StockCreateRequest request) {
        Hub hub = hubRepository.findById(request.hubId())
                .orElseThrow(() -> new BaseException(ErrorCode.HUB_NOT_FOUND));

        // 업체·상품이 실제로 있고, 요청자가 등록할 수 있으며, 허브가 업체의 소속 허브인지 Company에 확인한다. 등록 시점에만 검증한다.
        companyValidator.validateStockTarget(request.companyId(), hub.getId(), request.productId(), userId, role);

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
    public StockResponse inbound(UUID userId, String role, StockInboundRequest request) {
        if (!hubRepository.existsById(request.hubId())) {
            throw new BaseException(ErrorCode.HUB_NOT_FOUND);
        }

        // 등록과 같은 기준(MASTER 또는 업체 관리자, 소속 허브)으로 확인한다. Company 호출은 재고 행을 잠그기 전에 한다.
        companyValidator.validateStockTarget(request.companyId(), request.hubId(), request.productId(), userId, role);

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
     * 재고 변경과 DECREASE 이력은 한 트랜잭션이며, 같은 orderId를 다른 재고로 동시에 요청하면 UNIQUE(type, idempotency_key)의 (DECREASE, orderId)가 막는다.
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
        Stock stock = lockStock(original.getId());
        restore = findHistory(orderId, StockHistoryType.RESTORE);
        if (restore != null) {
            return replayRestore(restore, stock);
        }

        // 복원 수량은 최초 차감이 아니라 조정(ADJUST)까지 반영한 현재 점유 수량이다. 잠금 후 최신 이력으로 계산한다.
        long allocated = allocatedQuantity(stockHistoryRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId));
        if (allocated < 1) {
            throw new BaseException(ErrorCode.STOCK_ALLOCATION_UNDERFLOW);
        }
        if (allocated > Integer.MAX_VALUE) {
            throw new BaseException(ErrorCode.STOCK_QUANTITY_OVERFLOW);
        }
        int quantity = (int) allocated;
        try {
            stock.increase(quantity);
        } catch (ArithmeticException e) {
            throw new BaseException(ErrorCode.STOCK_QUANTITY_OVERFLOW);
        }
        saveHistory(StockHistory.restore(orderId, stock, quantity));
        return new StockRestoreResponse(orderId, stock.getId(), quantity, stock.getQuantity(), false);
    }

    /**
     * 주문 수량 수정에 따른 재고 조정. Order가 계산한 diff(수정 후 - 수정 전)를 quantityDelta로 받는다.
     * 멱등 키는 adjustmentId이며, 같은 작업의 재시도는 alreadyProcessed=true로 응답한다.
     * 재고 행을 잠근 뒤 이력을 다시 조회하므로 같은 주문의 조정·복원·차감과 동시에 들어와도 직렬화된다.
     * 서로 다른 재고에 같은 adjustmentId가 동시에 들어오는 경합은 UNIQUE(type, idempotency_key)의 (ADJUST, adjustmentId)가 막는다.
     */
    @Transactional
    public StockAdjustResponse adjust(StockAdjustRequest request) {
        int delta = request.quantityDelta();
        if (delta == 0) {
            throw new BaseException(ErrorCode.INVALID_QUANTITY_DELTA);
        }
        UUID orderId = request.orderId();

        // 1~4. 이미 처리된 조정이면 그 이력의 재고를, 아니면 최초 차감 이력의 재고를 대상으로 한다.
        StockHistory processed = stockHistoryRepository.findByTypeAndIdempotencyKey(StockHistoryType.ADJUST, request.adjustmentId()).orElse(null);
        Stock target;
        if (processed != null) {
            assertSameAdjustment(processed, request);
            target = processed.getStock();
        } else {
            StockHistory decrease = findHistory(orderId, StockHistoryType.DECREASE);
            if (decrease == null) {
                throw new BaseException(ErrorCode.DECREASE_HISTORY_NOT_FOUND);
            }
            target = decrease.getStock();
            if (!target.getHub().getId().equals(request.hubId()) || !target.getProductId().equals(request.productId())) {
                throw new BaseException(ErrorCode.STOCK_HISTORY_MISMATCH);
            }
        }

        // 5~6. 재고 행을 잠근 뒤 같은 조정과 주문 이력을 다시 조회한다. 삭제된 재고도 대상이다.
        Stock stock = lockStock(target.getId());
        processed = stockHistoryRepository.findByTypeAndIdempotencyKey(StockHistoryType.ADJUST, request.adjustmentId()).orElse(null);
        List<StockHistory> histories = stockHistoryRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId);

        // 7. 이미 처리된 조정이면 재고를 바꾸지 않고 최신 점유 수량·재고 수량으로 응답한다.
        if (processed != null) {
            assertSameAdjustment(processed, request);
            return new StockAdjustResponse(request.adjustmentId(), orderId, stock.getId(), processed.getQuantityDelta(),
                    toInt(allocatedQuantity(histories)), stock.getQuantity(), true);
        }

        // 8. 새로운 요청 검증. 복원 완료 주문은 조정할 수 없고, 조정 후 점유 수량은 1 이상 int 범위여야 한다.
        if (histories.stream().anyMatch(h -> h.getType() == StockHistoryType.RESTORE)) {
            throw new BaseException(ErrorCode.STOCK_ALREADY_RESTORED);
        }
        long allocatedAfter = allocatedQuantity(histories) + delta;
        if (allocatedAfter < 1) {
            throw new BaseException(ErrorCode.STOCK_ALLOCATION_UNDERFLOW);
        }
        if (allocatedAfter > Integer.MAX_VALUE) {
            throw new BaseException(ErrorCode.STOCK_QUANTITY_OVERFLOW);
        }

        // Integer.MIN_VALUE의 절댓값이 int를 넘지 않도록 long으로 계산한다.
        long amount = Math.abs((long) delta);
        if (delta > 0) {
            // 양수는 추가 차감이다. 삭제된 재고에는 새로 차감할 수 없다.
            if (stock.getDeletedAt() != null) {
                throw new BaseException(ErrorCode.STOCK_DELETED);
            }
            if (stock.getQuantity() < amount) {
                throw new BaseException(ErrorCode.INSUFFICIENT_STOCK);
            }
            stock.decrease((int) amount);
        } else {
            // 음수는 재고 반환이다. 삭제된 재고에도 허용하고 삭제 상태는 유지한다.
            try {
                stock.increase((int) amount);
            } catch (ArithmeticException e) {
                throw new BaseException(ErrorCode.STOCK_QUANTITY_OVERFLOW);
            }
        }

        // 9. UNIQUE 위반(같은 adjustmentId의 동시 처리)이면 추가 조회 없이 바로 예외를 던져 전체를 롤백한다.
        try {
            stockHistoryRepository.saveAndFlush(StockHistory.adjust(orderId, stock, request.adjustmentId(), delta));
        } catch (DataIntegrityViolationException e) {
            throw new BaseException(ErrorCode.STOCK_ADJUSTMENT_CONFLICT);
        }
        return new StockAdjustResponse(request.adjustmentId(), orderId, stock.getId(), delta,
                (int) allocatedAfter, stock.getQuantity(), false);
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

    // 재고 행을 쓰기 잠금으로 조회하고 최신 상태로 갱신한다. 이력의 getStock() 등으로 잠금 전에 읽어 둔 재고가 있으면
    // 영속성 컨텍스트에 남은 낡은 수량을 그대로 돌려주므로, 잠금 이후에 refresh해 다른 트랜잭션의 변경이 유실되지 않게 한다.
    private Stock lockStock(UUID stockId) {
        Stock stock = stockRepository.findByIdForUpdate(stockId)
                .orElseThrow(() -> new BaseException(ErrorCode.STOCK_NOT_FOUND));
        entityManager.refresh(stock);
        return stock;
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

    // 주문이 현재 점유한 수량 = DECREASE.quantity + 모든 ADJUST.quantityDelta. RESTORE가 있으면 0이다.
    // 중간 오버플로를 피하려고 long으로 합산한다.
    private static long allocatedQuantity(List<StockHistory> histories) {
        long total = 0;
        for (StockHistory history : histories) {
            switch (history.getType()) {
                case DECREASE -> total += history.getQuantity();
                case ADJUST -> total += history.getQuantityDelta();
                case RESTORE -> {
                    return 0;
                }
            }
        }
        return total;
    }

    private static int toInt(long value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new BaseException(ErrorCode.STOCK_QUANTITY_OVERFLOW);
        }
        return (int) value;
    }

    // 같은 adjustmentId는 같은 주문·허브·상품·증감량일 때만 같은 작업이다. 다르면 충돌이다.
    private static void assertSameAdjustment(StockHistory processed, StockAdjustRequest request) {
        Stock stock = processed.getStock();
        boolean same = processed.getOrderId().equals(request.orderId())
                && stock.getHub().getId().equals(request.hubId())
                && stock.getProductId().equals(request.productId())
                && processed.getQuantityDelta().equals(request.quantityDelta());
        if (!same) {
            throw new BaseException(ErrorCode.STOCK_ADJUSTMENT_CONFLICT);
        }
    }

    // UNIQUE(type, idempotency_key) 위반은 같은 주문 ID로 다른 재고를 동시에 처리한 경우다. 예외로 수량 변경도 함께 롤백된다.
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
