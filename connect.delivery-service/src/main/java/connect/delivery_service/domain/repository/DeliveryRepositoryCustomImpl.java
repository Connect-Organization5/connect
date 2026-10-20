package connect.delivery_service.domain.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import connect.delivery_service.client.user.AffiliationType;
import connect.delivery_service.domain.dto.DeliverySearchCondition;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static connect.delivery_service.domain.entity.QDelivery.delivery;
import static connect.delivery_service.domain.entity.QDeliveryRoute.deliveryRoute;

@RequiredArgsConstructor
public class DeliveryRepositoryCustomImpl implements DeliveryRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    public record DriverLoad(UUID userId, long activeCount, LocalDateTime lastDeliveryCreatedAt) {}

    @Override
    public Page<Delivery> search(DeliverySearchCondition cond, UUID userId,
                                 AffiliationType affiliationType, Pageable pageable) {
        BooleanExpression[] conditions = {
                statusEq(cond.status()),
                deliveryUserIdEq(cond.deliveryUserId()),
                departureHubIdEq(cond.departureHubId()),
                arrivalHubIdEq(cond.arrivalHubId()),
                receiverContains(cond.receiver()),
                onlyMyDeliveries(userId, affiliationType)
        };

        List<Delivery> content = queryFactory
                .selectFrom(delivery)
                .where(conditions)
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(delivery.count())
                .from(delivery)
                .where(conditions);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Map<UUID, DriverLoad> findDriverLoads(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        NumberExpression<Long> activeCount = new CaseBuilder()
                .when(delivery.deliveryStatus.in(
                        DeliveryStatus.HUB_WAITING,
                        DeliveryStatus.HUB_MOVING,
                        DeliveryStatus.DEST_HUB_ARRIVED,
                        DeliveryStatus.OUT_FOR_DELIVERY))
                .then(1L)
                .otherwise(0L)
                .sum();

        DateTimeExpression<LocalDateTime> lastDeliveryCreatedAt = delivery.createdAt.max();

        return queryFactory
                .select(delivery.deliveryUserId, activeCount, lastDeliveryCreatedAt)
                .from(delivery)
                .where(delivery.deliveryUserId.in(userIds))
                .groupBy(delivery.deliveryUserId)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> t.get(delivery.deliveryUserId),
                        t -> new DriverLoad(
                                t.get(delivery.deliveryUserId),
                                t.get(activeCount),
                                t.get(lastDeliveryCreatedAt))));
    }

    private BooleanExpression statusEq(DeliveryStatus status) {
        return status != null ? delivery.deliveryStatus.eq(status) : null;
    }

    private BooleanExpression deliveryUserIdEq(UUID deliveryUserId) {
        return deliveryUserId != null ? delivery.deliveryUserId.eq(deliveryUserId) : null;
    }

    private BooleanExpression departureHubIdEq(UUID departureHubId) {
        return departureHubId != null ? delivery.departureHubId.eq(departureHubId) : null;
    }

    private BooleanExpression arrivalHubIdEq(UUID arrivalHubId) {
        return arrivalHubId != null ? delivery.arrivalHubId.eq(arrivalHubId) : null;
    }

    private BooleanExpression receiverContains(String receiver) {
        return (receiver != null && !receiver.isBlank())
                ? delivery.receiver.containsIgnoreCase(receiver.trim())
                : null;
    }

    private BooleanExpression onlyMyDeliveries(UUID userId, AffiliationType affiliationType) {
        if (affiliationType != AffiliationType.HUB) {
            return null;
        }

        return delivery.deliveryUserId.eq(userId)
                .or(delivery.id.in(
                        JPAExpressions
                                .select(deliveryRoute.deliveryId)
                                .from(deliveryRoute)
                                .where(deliveryRoute.deliveryUserId.eq(userId))
                ));
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (Sort.Order o : sort) {
            Order direction = o.isAscending() ? Order.ASC : Order.DESC;
            switch (o.getProperty()) {
                case "createdAt" -> orders.add(new OrderSpecifier<>(direction, delivery.createdAt));
                case "updatedAt" -> orders.add(new OrderSpecifier<>(direction, delivery.updatedAt));
                default -> { }
            }
        }

        if (orders.isEmpty()) {
            orders.add(delivery.createdAt.desc());
        }
        return orders.toArray(new OrderSpecifier[0]);
    }
}
