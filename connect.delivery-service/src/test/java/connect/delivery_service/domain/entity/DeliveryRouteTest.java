package connect.delivery_service.domain.entity;

import connect.delivery_service.global.exception.BaseException;
import connect.delivery_service.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryRouteTest {

    private final UUID deliveryUserId = UUID.randomUUID();

    private DeliveryRoute deliveryRoute;

    @BeforeEach
    void setUp() {
        deliveryRoute = DeliveryRoute.createDeliveryRoute(UUID.randomUUID(), deliveryUserId,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(12.5), 30);
    }

    @Test
    @DisplayName("경로 생성 시 상태는 WAITING, 실제 이동거리/소요시간은 비어있음")
    void create() {
        assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.WAITING);
        assertThat(deliveryRoute.getEstimatedDistance()).isEqualByComparingTo("12.5");
        assertThat(deliveryRoute.getEstimatedDuration()).isEqualTo(30);
        assertThat(deliveryRoute.getActualDistance()).isNull();
        assertThat(deliveryRoute.getActualDuration()).isNull();
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("정상 흐름: WAITING -> MOVING -> ARRIVED, 도착 시 실제 이동거리/소요시간 기록")
        void fullFlow() {
            deliveryRoute.startMoving();
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.MOVING);

            deliveryRoute.complete(BigDecimal.valueOf(13.2), 35);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.ARRIVED);
            assertThat(deliveryRoute.getActualDistance()).isEqualByComparingTo("13.2");
            assertThat(deliveryRoute.getActualDuration()).isEqualTo(35);
        }

        @Test
        @DisplayName("출발 전에는 도착 처리 불가, 실제 기록도 남지 않음")
        void completeBeforeStart() {
            assertThatThrownBy(() -> deliveryRoute.complete(BigDecimal.TEN, 10))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(deliveryRoute.getActualDistance()).isNull();
            assertThat(deliveryRoute.getActualDuration()).isNull();
        }

        @Test
        @DisplayName("도착 후에는 다시 출발 불가")
        void startAfterArrived() {
            deliveryRoute.startMoving();
            deliveryRoute.complete(BigDecimal.TEN, 10);

            assertThatThrownBy(() -> deliveryRoute.startMoving())
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("WAITING 이면 취소 가능")
        void cancelWhenWaiting() {
            deliveryRoute.cancel();

            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.CANCELED);
        }

        @Test
        @DisplayName("이동 중에는 취소 불가")
        void cancelWhenMoving() {
            deliveryRoute.startMoving();

            assertThatThrownBy(() -> deliveryRoute.cancel())
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.MOVING);
        }
    }

    @Nested
    @DisplayName("담당자 변경")
    class ChangeDeliveryUser {

        @Test
        @DisplayName("WAITING 이면 담당자 변경 가능")
        void changeWhenWaiting() {
            UUID newDeliveryUserId = UUID.randomUUID();

            deliveryRoute.changeDeliveryUserId(newDeliveryUserId);

            assertThat(deliveryRoute.getDeliveryUserId()).isEqualTo(newDeliveryUserId);
        }

        @Test
        @DisplayName("이동 중에는 담당자 변경 불가")
        void changeWhenMoving() {
            deliveryRoute.startMoving();

            assertThatThrownBy(() -> deliveryRoute.changeDeliveryUserId(UUID.randomUUID()))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(deliveryRoute.getDeliveryUserId()).isEqualTo(deliveryUserId);
        }
    }

    @Test
    @DisplayName("삭제 시 삭제 정보 기록")
    void delete() {
        UUID userId = UUID.randomUUID();

        deliveryRoute.delete(userId);

        assertThat(deliveryRoute.isDeleted()).isTrue();
        assertThat(deliveryRoute.getDeletedBy()).isEqualTo(userId);
    }
}
