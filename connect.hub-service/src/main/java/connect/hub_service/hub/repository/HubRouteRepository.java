package connect.hub_service.hub.repository;

import connect.hub_service.hub.entity.HubRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HubRouteRepository extends JpaRepository<HubRoute, UUID> {

    Page<HubRoute> findByDepartureHubId(UUID departureHubId, Pageable pageable);

    Page<HubRoute> findByArrivalHubId(UUID arrivalHubId, Pageable pageable);

    Page<HubRoute> findByDepartureHubIdAndArrivalHubId(UUID departureHubId, UUID arrivalHubId, Pageable pageable);

    Optional<HubRoute> findByDepartureHubIdAndArrivalHubId(UUID departureHubId, UUID arrivalHubId);

    boolean existsByDepartureHubIdAndArrivalHubId(UUID departureHubId, UUID arrivalHubId);
}
