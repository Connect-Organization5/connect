package connect.delivery_service.domain.repository;

import connect.delivery_service.domain.entity.DeliveryRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {
    Optional<DeliveryRoute> findByDeliveryId(UUID id);
}
