package connect.order_service.domain.repository;

import connect.order_service.domain.entity.Order;
import connect.order_service.domain.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndDeletedAtIsNull(UUID orderId);

    Page<Order> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Order> findAllByCreatedByAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Page<Order> findAllBySupplierCompanyIdAndStatusNotAndDeletedAtIsNull(UUID supplierCompanyId, OrderStatus orderStatus, Pageable pageable);
}
