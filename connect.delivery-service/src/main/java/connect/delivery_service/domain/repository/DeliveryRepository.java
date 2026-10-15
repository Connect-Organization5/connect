package connect.delivery_service.domain.repository;

import connect.delivery_service.domain.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, DeliveryRepositoryCustom {
    Optional<Delivery> findByOrderId(UUID orderId);
}
