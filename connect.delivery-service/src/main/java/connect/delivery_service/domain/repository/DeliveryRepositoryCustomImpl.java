package connect.delivery_service.domain.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import connect.delivery_service.client.AffiliationType;
import connect.delivery_service.domain.dto.DeliverySearchCondition;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static connect.delivery_service.domain.entity.QDelivery.delivery;
import static connect.delivery_service.domain.entity.QDeliveryRoute.deliveryRoute;

@RequiredArgsConstructor
public class DeliveryRepositoryCustomImpl implements DeliveryRepositoryCustom {
    private final JPAQueryFactory queryFactory;

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

        // 마지막 페이지 등 count 가 필요 없는 경우엔 count 쿼리를 생략해준다
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // ===== 검색 조건 (null 을 반환하면 where 에서 무시됨) =====

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

    // ===== 권한 조건: 허브 소속(배송담당자)은 본인이 담당한 배송만 =====

    private BooleanExpression onlyMyDeliveries(UUID userId, AffiliationType affiliationType) {
        if (affiliationType != AffiliationType.HUB) {
            return null; // MASTER, 업체 소속은 전체 조회 (발제 기준)
        }

        return delivery.deliveryUserId.eq(userId)              // 업체 배송 담당
                .or(delivery.id.in(                             // 허브 간 이동 담당
                        JPAExpressions
                                .select(deliveryRoute.deliveryId)
                                .from(deliveryRoute)
                                .where(deliveryRoute.deliveryUserId.eq(userId))
                ));
    }

    // ===== 정렬 (허용 필드는 DeliverySearchCondition 에서 이미 걸러짐) =====

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (Sort.Order o : sort) {
            Order direction = o.isAscending() ? Order.ASC : Order.DESC;
            switch (o.getProperty()) {
                case "createdAt" -> orders.add(new OrderSpecifier<>(direction, delivery.createdAt));
                case "updatedAt" -> orders.add(new OrderSpecifier<>(direction, delivery.updatedAt));
                default -> { /* 허용되지 않은 필드는 무시 */ }
            }
        }

        if (orders.isEmpty()) {
            orders.add(delivery.createdAt.desc());
        }
        return orders.toArray(new OrderSpecifier[0]);
    }
}
