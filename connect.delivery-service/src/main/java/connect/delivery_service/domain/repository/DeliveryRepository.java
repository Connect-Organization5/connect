package connect.delivery_service.domain.repository;

import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, DeliveryRepositoryCustom {
    Optional<Delivery> findByOrderId(UUID orderId);

    Page<Delivery> findAllByDeliveryUserIdIsNullAndDeliveryStatusAndDepartureHubId(
            DeliveryStatus status, UUID departureHubId, Pageable pageable);
}
