package connect.delivery_service.domain.repository;

import connect.delivery_service.client.AffiliationType;
import connect.delivery_service.domain.dto.DeliverySearchCondition;
import connect.delivery_service.domain.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DeliveryRepositoryCustom {
    Page<Delivery> search(DeliverySearchCondition condition, UUID userId,
                          AffiliationType affiliationType, Pageable pageable);
}
