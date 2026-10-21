package connect.delivery_service.domain.entity;

import connect.delivery_service.global.exception.BaseException;
import connect.delivery_service.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    private final UUID ordererId = UUID.randomUUID();
    private final UUID deliveryUserId = UUID.randomUUID();

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        delivery = Delivery.createDelivery(UUID.randomUUID(), ordererId,
                UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");
        delivery.assignDeliveryUserId(deliveryUserId);
    }

    // ===== 원하는 상태까지 전이시키는 헬퍼 =====

    private void moveToDestHubArrived() {
        delivery.startHubMoving();
        delivery.arrivedHub();
    }

    private void moveToDelivered() {
        moveToDestHubArrived();
        delivery.startDelivery();
        delivery.complete();
    }

    @Test
    @DisplayName("배송 생성 시 상태는 HUB_WAITING")
    void create() {
        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
        assertThat(delivery.getOrdererId()).isEqualTo(ordererId);
        assertThat(delivery.getDeliveryUserId()).isEqualTo(deliveryUserId);
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("정상 흐름: HUB_WAITING -> HUB_MOVING -> DEST_HUB_ARRIVED -> OUT_FOR_DELIVERY -> DELIVERED")
        void fullFlow() {
            delivery.startHubMoving();
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);

            delivery.arrivedHub();
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DEST_HUB_ARRIVED);

            delivery.startDelivery();
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);

            delivery.complete();
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("단계를 건너뛰면 예외 (HUB_WAITING 에서 바로 complete)")
        void skipStep() {
            assertThatThrownBy(() -> delivery.complete())
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
        }

        @Test
        @DisplayName("담당자가 없으면 허브 출발 불가, 상태 유지")
        void startHubMovingWithoutDeliveryUser() {
            Delivery unassigned = Delivery.createDelivery(UUID.randomUUID(), ordererId,
                    UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");

            assertThatThrownBy(unassigned::startHubMoving)
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_USER_NOT_ASSIGNED);
            assertThat(unassigned.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
        }

        @Test
        @DisplayName("담당자를 배정하면 허브 출발 가능")
        void startHubMovingAfterAssign() {
            Delivery unassigned = Delivery.createDelivery(UUID.randomUUID(), ordererId,
                    UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");
            unassigned.assignDeliveryUserId(deliveryUserId);

            unassigned.startHubMoving();

            assertThat(unassigned.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);
        }

        @Test
        @DisplayName("담당자가 없어도 취소는 가능")
        void cancelWithoutDeliveryUser() {
            Delivery unassigned = Delivery.createDelivery(UUID.randomUUID(), ordererId,
                    UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");

            unassigned.cancel();

            assertThat(unassigned.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
        }

        @Test
        @DisplayName("상태 검증이 먼저: 취소된 미배정 배송은 INVALID_DELIVERY_STATUS")
        void statusCheckedBeforeAssignment() {
            Delivery unassigned = Delivery.createDelivery(UUID.randomUUID(), ordererId,
                    UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");
            unassigned.cancel();

            assertThatThrownBy(unassigned::startHubMoving)
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
        }

        @Test
        @DisplayName("같은 전이를 두 번 하면 예외")
        void duplicateTransition() {
            delivery.startHubMoving();

            assertThatThrownBy(() -> delivery.startHubMoving())
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
        }

        @Test
        @DisplayName("완료된 배송은 더 이상 전이 불가")
        void afterDelivered() {
            moveToDelivered();

            assertThatThrownBy(() -> delivery.startHubMoving()).isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThatThrownBy(() -> delivery.cancel()).isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("HUB_WAITING 이면 취소 가능")
        void cancelWhenWaiting() {
            delivery.cancel();

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
        }

        @Test
        @DisplayName("출발 후에는 취소 불가")
        void cancelAfterStart() {
            delivery.startHubMoving();

            assertThatThrownBy(() -> delivery.cancel())
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);
        }

        @Test
        @DisplayName("취소된 배송은 출발 불가")
        void startAfterCancel() {
            delivery.cancel();

            assertThatThrownBy(() -> delivery.startHubMoving())
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
        }
    }

    @Nested
    @DisplayName("담당자 / 수령인 변경")
    class Change {

        @Test
        @DisplayName("HUB_WAITING 이면 담당자 변경 가능")
        void changeDeliveryUserId() {
            UUID newDeliveryUserId = UUID.randomUUID();

            delivery.changeDeliveryUserId(newDeliveryUserId);

            assertThat(delivery.getDeliveryUserId()).isEqualTo(newDeliveryUserId);
            assertThat(delivery.isManagedBy(newDeliveryUserId)).isTrue();
            assertThat(delivery.isManagedBy(deliveryUserId)).isFalse();
        }

        @Test
        @DisplayName("담당자 배정 시 배정 시각 기록, 변경하면 변경 시각으로 갱신")
        void assignedAt() {
            Delivery unassigned = Delivery.createDelivery(UUID.randomUUID(), ordererId,
                    UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");
            assertThat(unassigned.getAssignedAt()).isNull();

            unassigned.assignDeliveryUserId(deliveryUserId);
            LocalDateTime assignedAt = unassigned.getAssignedAt();
            assertThat(assignedAt).isNotNull();

            unassigned.changeDeliveryUserId(UUID.randomUUID());
            assertThat(unassigned.getAssignedAt()).isAfterOrEqualTo(assignedAt);
        }

        @Test
        @DisplayName("출발 후에는 담당자 변경 불가")
        void changeDeliveryUserIdAfterStart() {
            delivery.startHubMoving();

            assertThatThrownBy(() -> delivery.changeDeliveryUserId(UUID.randomUUID()))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(delivery.getDeliveryUserId()).isEqualTo(deliveryUserId);
        }

        @Test
        @DisplayName("HUB_WAITING 이면 수령인 변경 가능")
        void changeReceiver() {
            delivery.changeReceiver("김철수");

            assertThat(delivery.getReceiver()).isEqualTo("김철수");
        }

        @Test
        @DisplayName("출발 후에는 수령인 변경 불가")
        void changeReceiverAfterStart() {
            delivery.startHubMoving();

            assertThatThrownBy(() -> delivery.changeReceiver("김철수"))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(delivery.getReceiver()).isEqualTo("홍길동");
        }
    }

    @Nested
    @DisplayName("본인 / 담당자 확인")
    class Ownership {

        @Test
        @DisplayName("주문자 확인")
        void isOrderedBy() {
            assertThat(delivery.isOrderedBy(ordererId)).isTrue();
            assertThat(delivery.isOrderedBy(UUID.randomUUID())).isFalse();
            assertThat(delivery.isOrderedBy(null)).isFalse();
        }

        @Test
        @DisplayName("담당자 확인")
        void isManagedBy() {
            assertThat(delivery.isManagedBy(deliveryUserId)).isTrue();
            assertThat(delivery.isManagedBy(UUID.randomUUID())).isFalse();
            assertThat(delivery.isManagedBy(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("DELIVERED 이면 삭제 가능")
        void deleteWhenDelivered() {
            moveToDelivered();
            UUID userId = UUID.randomUUID();

            delivery.delete(userId);

            assertThat(delivery.isDeleted()).isTrue();
            assertThat(delivery.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("CANCELED 이면 삭제 가능")
        void deleteWhenCanceled() {
            delivery.cancel();

            delivery.delete(UUID.randomUUID());

            assertThat(delivery.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("진행 중인 배송은 삭제 불가")
        void deleteInProgress() {
            assertThatThrownBy(() -> delivery.delete(UUID.randomUUID()))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);

            moveToDestHubArrived();
            assertThatThrownBy(() -> delivery.delete(UUID.randomUUID()))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);

            assertThat(delivery.isDeleted()).isFalse();
        }
    }
}
