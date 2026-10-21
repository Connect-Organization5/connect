package connect.hub_service.stock.repository;

import connect.hub_service.stock.entity.StockInbound;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockInboundRepository extends JpaRepository<StockInbound, UUID> {

    // UNIQUE(inbound_id)가 있으므로 결과는 최대 한 건이다.
    Optional<StockInbound> findByInboundId(UUID inboundId);

    // 재고별 입고 이력. 정렬은 Pageable로 받는다(최신순).
    Page<StockInbound> findByStockId(UUID stockId, Pageable pageable);
}
