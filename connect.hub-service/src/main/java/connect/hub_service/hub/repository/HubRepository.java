package connect.hub_service.hub.repository;

import connect.hub_service.hub.entity.Hub;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HubRepository extends JpaRepository<Hub, UUID> {

    // 허브명 또는 주소에 keyword가 포함된 허브 (와일드카드 문자는 Spring Data가 이스케이프)
    Page<Hub> findByNameContainingOrAddressContaining(String name, String address, Pageable pageable);

    Optional<Hub> findByName(String name);
}
