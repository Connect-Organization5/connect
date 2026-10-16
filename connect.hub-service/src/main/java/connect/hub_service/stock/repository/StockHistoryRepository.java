package connect.hub_service.stock.repository;

import connect.hub_service.stock.entity.StockHistory;
import connect.hub_service.stock.entity.StockHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockHistoryRepository extends JpaRepository<StockHistory, UUID> {

    Optional<StockHistory> findByOrderIdAndType(UUID orderId, StockHistoryType type);

    // 멱등 키로 이미 처리된 이력을 찾는다. 조정은 findByTypeAndIdempotencyKey(ADJUST, adjustmentId)로 조회한다.
    // UNIQUE(type, idempotency_key)가 있으므로 결과는 최대 한 건이다.
    Optional<StockHistory> findByTypeAndIdempotencyKey(StockHistoryType type, UUID idempotencyKey);

    List<StockHistory> findAllByOrderIdAndType(UUID orderId, StockHistoryType type);

    // 처리 시각 오름차순, 동일 시각이면 id 오름차순. 주문 점유 수량 합산에도 쓴다.
    List<StockHistory> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);
}
