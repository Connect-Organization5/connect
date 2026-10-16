package connect.hub_service.stock.repository;

import connect.hub_service.stock.entity.StockHistory;
import connect.hub_service.stock.entity.StockHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockHistoryRepository extends JpaRepository<StockHistory, UUID> {

    Optional<StockHistory> findByOrderIdAndType(UUID orderId, StockHistoryType type);

    // 처리 시각 오름차순, 동일 시각이면 id 오름차순
    List<StockHistory> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);
}
