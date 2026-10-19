package connect.hub_service.stock.repository;

import connect.hub_service.stock.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    // 일반 조회는 논리 삭제된 재고를 제외한다.
    Page<Stock> findByDeletedAtIsNull(Pageable pageable);

    Page<Stock> findByHubIdAndDeletedAtIsNull(UUID hubId, Pageable pageable);

    Page<Stock> findByProductIdAndDeletedAtIsNull(UUID productId, Pageable pageable);

    Page<Stock> findByHubIdAndProductIdAndDeletedAtIsNull(UUID hubId, UUID productId, Pageable pageable);

    Optional<Stock> findByIdAndDeletedAtIsNull(UUID id);

    // 입고 등 수량 변경용 쓰기 잠금 조회. 삭제된 재고도 조회해 호출 측이 404/409를 구분한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.hub.id = :hubId and s.productId = :productId")
    Optional<Stock> findByHubIdAndProductIdForUpdate(@Param("hubId") UUID hubId, @Param("productId") UUID productId);

    // 주문 복원용 쓰기 잠금 조회. 원본 차감 이력의 재고를 찾으므로 삭제된 재고도 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Optional<Stock> findByIdForUpdate(@Param("id") UUID id);

    // 일괄 논리 삭제용 쓰기 잠금 조회. 전달된 상품들이 등록된 모든 허브의 활성 재고가 대상이다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.productId in :productIds and s.deletedAt is null")
    List<Stock> findActiveByProductIdsForUpdate(@Param("productIds") Collection<UUID> productIds);

    // UNIQUE(hub_id, product_id)는 삭제된 재고에도 적용되므로 삭제 여부와 무관하게 확인한다.
    boolean existsByHubIdAndProductId(UUID hubId, UUID productId);
}
