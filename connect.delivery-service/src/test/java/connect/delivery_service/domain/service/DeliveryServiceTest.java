package connect.delivery_service.domain.service;

import connect.delivery_service.client.RoleType;
import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryRoute;
import connect.delivery_service.domain.entity.DeliveryRouteStatus;
import connect.delivery_service.domain.entity.DeliveryStatus;
import connect.delivery_service.domain.repository.DeliveryRepository;
import connect.delivery_service.domain.repository.DeliveryRouteRepository;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryRouteRepository deliveryRouteRepository;

    @InjectMocks
    private DeliveryService deliveryService;

    private final UUID deliveryId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID ordererId = UUID.randomUUID();
    private final UUID deliveryUserId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private Delivery delivery;
    private DeliveryRoute deliveryRoute;

    @BeforeEach
    void setUp() {
        delivery = Delivery.createDelivery(orderId, ordererId, deliveryUserId,
                UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");
        // id 는 DB 저장 시 생성되므로 테스트에서는 직접 넣어준다
        ReflectionTestUtils.setField(delivery, "id", deliveryId);

        deliveryRoute = DeliveryRoute.createDeliveryRoute(deliveryId, deliveryUserId,
                delivery.getDepartureHubId(), delivery.getArrivalHubId(), BigDecimal.TEN, 30);
    }

    private void givenDelivery() {
        given(deliveryRepository.findById(deliveryId)).willReturn(Optional.of(delivery));
    }

    private void givenDeliveryRoute() {
        given(deliveryRouteRepository.findByDeliveryId(deliveryId)).willReturn(Optional.of(deliveryRoute));
    }

    @Nested
    @DisplayName("배송 생성")
    class CreateDelivery {

        private CreateDeliveryRequest request;

        @BeforeEach
        void setUp() {
            request = new CreateDeliveryRequest();
            ReflectionTestUtils.setField(request, "orderId", orderId);
            ReflectionTestUtils.setField(request, "ordererId", ordererId);
            ReflectionTestUtils.setField(request, "deliveryUserId", deliveryUserId);
            ReflectionTestUtils.setField(request, "departureHubId", UUID.randomUUID());
            ReflectionTestUtils.setField(request, "arrivalHubId", UUID.randomUUID());
            ReflectionTestUtils.setField(request, "receiver", "홍길동");
            ReflectionTestUtils.setField(request, "deliveryAddress", "서울시 강남구");
        }

        @Test
        @DisplayName("새 주문이면 배송과 배송 경로를 저장")
        void createNew() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            DeliveryResponse response = deliveryService.createDelivery(request);

            assertThat(response.getOrderId()).isEqualTo(orderId);
            assertThat(response.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
            verify(deliveryRepository).save(any(Delivery.class));

            ArgumentCaptor<DeliveryRoute> routeCaptor = ArgumentCaptor.forClass(DeliveryRoute.class);
            verify(deliveryRouteRepository).save(routeCaptor.capture());
            DeliveryRoute savedRoute = routeCaptor.getValue();
            assertThat(savedRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.WAITING);
            assertThat(savedRoute.getDeliveryUserId()).isEqualTo(deliveryUserId);
        }

        @Test
        @DisplayName("이미 배송이 있는 주문이면 저장하지 않고 기존 배송 반환")
        void alreadyExists() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.of(delivery));

            DeliveryResponse response = deliveryService.createDelivery(request);

            assertThat(response.getId()).isEqualTo(deliveryId);
            verify(deliveryRepository, never()).save(any());
            verifyNoInteractions(deliveryRouteRepository);
        }
    }

    @Nested
    @DisplayName("단건 조회")
    class GetDelivery {

        @Test
        @DisplayName("주문자는 조회 가능")
        void byOrderer() {
            givenDelivery();

            assertThat(deliveryService.getDelivery(deliveryId, ordererId, RoleType.USER).getId())
                    .isEqualTo(deliveryId);
        }

        @Test
        @DisplayName("담당자는 조회 가능")
        void byDeliveryUser() {
            givenDelivery();

            assertThat(deliveryService.getDelivery(deliveryId, deliveryUserId, RoleType.USER).getId())
                    .isEqualTo(deliveryId);
        }

        @Test
        @DisplayName("MASTER 는 조회 가능")
        void byMaster() {
            givenDelivery();

            assertThat(deliveryService.getDelivery(deliveryId, otherUserId, RoleType.MASTER).getId())
                    .isEqualTo(deliveryId);
        }

        @Test
        @DisplayName("관련 없는 사용자는 404")
        void byOther() {
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.getDelivery(deliveryId, otherUserId, RoleType.USER))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("없는 배송이면 404")
        void notFound() {
            given(deliveryRepository.findById(deliveryId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryService.getDelivery(deliveryId, ordererId, RoleType.USER))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("배송 경로 기록 조회")
    class GetDeliveryRoute {

        @Test
        @DisplayName("경로 담당자는 조회 가능, 배송은 조회하지 않음")
        void byRouteManager() {
            givenDeliveryRoute();

            assertThat(deliveryService.getDeliveryRoute(deliveryId, deliveryUserId, RoleType.USER).getDeliveryId())
                    .isEqualTo(deliveryId);
            verifyNoInteractions(deliveryRepository);
        }

        @Test
        @DisplayName("MASTER 는 조회 가능")
        void byMaster() {
            givenDeliveryRoute();

            assertThat(deliveryService.getDeliveryRoute(deliveryId, otherUserId, RoleType.MASTER).getDeliveryId())
                    .isEqualTo(deliveryId);
        }

        @Test
        @DisplayName("주문자는 조회 가능")
        void byOrderer() {
            givenDeliveryRoute();
            givenDelivery();

            assertThat(deliveryService.getDeliveryRoute(deliveryId, ordererId, RoleType.USER).getDeliveryId())
                    .isEqualTo(deliveryId);
        }

        @Test
        @DisplayName("관련 없는 사용자는 404")
        void byOther() {
            givenDeliveryRoute();
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.getDeliveryRoute(deliveryId, otherUserId, RoleType.USER))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("배송 상태 변경")
    class StatusChange {

        @Test
        @DisplayName("담당자가 허브 출발 처리하면 배송, 배송 경로 모두 이동 중")
        void startHubMoving() {
            givenDelivery();
            givenDeliveryRoute();

            deliveryService.startHubMoving(deliveryId, deliveryUserId);

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.MOVING);
        }

        @Test
        @DisplayName("담당자가 아니면 404, 상태 변경 없음")
        void startHubMovingByOther() {
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.startHubMoving(deliveryId, otherUserId))
                    .isInstanceOf(NotFoundException.class);
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("배송 경로가 없으면 404")
        void startHubMovingWithoutRoute() {
            givenDelivery();
            given(deliveryRouteRepository.findByDeliveryId(deliveryId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryService.startHubMoving(deliveryId, deliveryUserId))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("목적지 허브 도착 처리하면 배송은 DEST_HUB_ARRIVED, 배송 경로는 ARRIVED + 실제 기록")
        void arriveAtDestinationHub() {
            givenDelivery();
            givenDeliveryRoute();
            delivery.startHubMoving();
            deliveryRoute.startMoving();

            deliveryService.arriveAtDestinationHub(deliveryId, deliveryUserId);

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DEST_HUB_ARRIVED);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.ARRIVED);
            assertThat(deliveryRoute.getActualDistance()).isNotNull();
            assertThat(deliveryRoute.getActualDuration()).isNotNull();
        }

        @Test
        @DisplayName("업체 배송 시작 -> 완료")
        void startAndCompleteDelivery() {
            givenDelivery();
            delivery.startHubMoving();
            delivery.arrivedHub();

            deliveryService.startDelivery(deliveryId, deliveryUserId);
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);

            deliveryService.completeDelivery(deliveryId, deliveryUserId);
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        }
    }

    @Nested
    @DisplayName("주문 취소에 따른 배송 취소")
    class CancelByOrderId {

        @Test
        @DisplayName("대기 중이면 배송, 배송 경로 모두 취소")
        void cancel() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.of(delivery));
            givenDeliveryRoute();

            deliveryService.cancelByOrderId(orderId);

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.CANCELED);
        }

        @Test
        @DisplayName("이미 취소된 배송이면 아무것도 하지 않음 (재시도 안전)")
        void alreadyCanceled() {
            delivery.cancel();
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.of(delivery));

            deliveryService.cancelByOrderId(orderId);

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.CANCELED);
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("배송이 없으면 아무것도 하지 않음")
        void notFound() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            deliveryService.cancelByOrderId(orderId);

            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("이미 출발한 배송이면 예외")
        void afterStart() {
            delivery.startHubMoving();
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.of(delivery));

            assertThatThrownBy(() -> deliveryService.cancelByOrderId(orderId))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(deliveryRouteRepository);
        }
    }

    @Nested
    @DisplayName("담당자 변경")
    class ChangeDeliveryUserId {

        @Test
        @DisplayName("MASTER 는 배송, 배송 경로 담당자 모두 변경")
        void byMaster() {
            givenDelivery();
            givenDeliveryRoute();
            UUID newDeliveryUserId = UUID.randomUUID();

            DeliveryResponse response = deliveryService.changeDeliveryUserId(deliveryId, RoleType.MASTER, newDeliveryUserId);

            assertThat(response.getDeliveryUserId()).isEqualTo(newDeliveryUserId);
            assertThat(delivery.getDeliveryUserId()).isEqualTo(newDeliveryUserId);
            assertThat(deliveryRoute.getDeliveryUserId()).isEqualTo(newDeliveryUserId);
        }

        @Test
        @DisplayName("MASTER 가 아니면 403, 조회도 하지 않음")
        void byUser() {
            assertThatThrownBy(() -> deliveryService.changeDeliveryUserId(deliveryId, RoleType.USER, UUID.randomUUID()))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(deliveryRepository, deliveryRouteRepository);
        }
    }

    @Nested
    @DisplayName("수령인 변경")
    class ChangeReceiver {

        @Test
        @DisplayName("주문자는 변경 가능")
        void byOrderer() {
            givenDelivery();

            DeliveryResponse response = deliveryService.changeReceiver(deliveryId, ordererId, RoleType.USER, "김철수");

            assertThat(response.getReceiver()).isEqualTo("김철수");
        }

        @Test
        @DisplayName("MASTER 는 변경 가능")
        void byMaster() {
            givenDelivery();

            deliveryService.changeReceiver(deliveryId, otherUserId, RoleType.MASTER, "김철수");

            assertThat(delivery.getReceiver()).isEqualTo("김철수");
        }

        @Test
        @DisplayName("담당자라도 주문자가 아니면 404")
        void byDeliveryUser() {
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.changeReceiver(deliveryId, deliveryUserId, RoleType.USER, "김철수"))
                    .isInstanceOf(NotFoundException.class);
            assertThat(delivery.getReceiver()).isEqualTo("홍길동");
        }
    }

    @Nested
    @DisplayName("배송 삭제")
    class DeleteDelivery {

        @Test
        @DisplayName("주문자가 끝난 배송을 삭제하면 배송, 배송 경로 모두 삭제")
        void byOrderer() {
            delivery.cancel();
            givenDelivery();
            givenDeliveryRoute();

            deliveryService.deleteDelivery(deliveryId, ordererId, RoleType.USER);

            assertThat(delivery.isDeleted()).isTrue();
            assertThat(deliveryRoute.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("진행 중인 배송이면 예외, 배송 경로도 삭제되지 않음")
        void inProgress() {
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.deleteDelivery(deliveryId, ordererId, RoleType.USER))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(delivery.isDeleted()).isFalse();
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("주문자도 MASTER 도 아니면 404")
        void byOther() {
            delivery.cancel();
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.deleteDelivery(deliveryId, otherUserId, RoleType.USER))
                    .isInstanceOf(NotFoundException.class);
            assertThat(delivery.isDeleted()).isFalse();
        }
    }
}
