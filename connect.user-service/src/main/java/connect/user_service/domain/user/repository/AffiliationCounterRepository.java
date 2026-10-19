package connect.user_service.domain.user.repository;

import connect.user_service.domain.user.entity.AffiliationCounter;
import connect.user_service.domain.user.entity.AffiliationCounterId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AffiliationCounterRepository extends JpaRepository<AffiliationCounter, AffiliationCounterId> {

    // 같은 소속의 가입이 동시에 들어와도 한 번에 하나씩 처리되도록 행 잠금 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AffiliationCounter c WHERE c.id = :id")
    Optional<AffiliationCounter> findByIdForUpdate(@Param("id") AffiliationCounterId id);
}
