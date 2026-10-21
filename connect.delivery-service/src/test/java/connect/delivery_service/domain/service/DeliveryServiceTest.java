package connect.delivery_service.domain.service;

import connect.delivery_service.global.exception.BaseException;
import connect.delivery_service.global.exception.ErrorCode;
import connect.delivery_service.client.user.AffiliationType;
import connect.delivery_service.client.hub.HubClient;
import connect.delivery_service.client.hub.HubRouteResponse;
import connect.delivery_service.client.user.RoleType;
import connect.delivery_service.client.user.UserClient;
import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.dto.DeliveryResponse;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryRoute;
import connect.delivery_service.domain.entity.DeliveryRouteStatus;
import connect.delivery_service.domain.entity.DeliveryStatus;
import connect.delivery_service.domain.repository.DeliveryRepository;
import connect.delivery_service.domain.repository.DeliveryRepositoryCustomImpl.DriverLoad;
import connect.delivery_service.domain.repository.DeliveryRouteRepository;
import connect.delivery_service.global.dto.ApiResponse;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import connect.delivery_service.global.dto.PagedResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    @Mock
    private HubClient hubClient;

    @Mock
    private UserClient userClient;

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
        delivery = Delivery.createDelivery(orderId, ordererId,
                UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");
        delivery.assignDeliveryUserId(deliveryUserId);
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
            ReflectionTestUtils.setField(request, "departureHubId", UUID.randomUUID());
            ReflectionTestUtils.setField(request, "arrivalHubId", UUID.randomUUID());
            ReflectionTestUtils.setField(request, "receiver", "홍길동");
            ReflectionTestUtils.setField(request, "deliveryAddress", "서울시 강남구");
        }

        private void givenHubRoute(BigDecimal distance, Integer duration) {
            HubRouteResponse hubRoute = new HubRouteResponse(UUID.randomUUID(),
                    request.getDepartureHubId(), request.getArrivalHubId(), distance, duration);
            given(hubClient.getRoute(request.getDepartureHubId(), request.getArrivalHubId()))
                    .willReturn(ApiResponse.ok("이동 경로 조회 성공", hubRoute));
        }

        // user-service 의 허브 배송 담당자 목록과 담당자별 배송 부하를 mock 으로 넣는다
        private void givenHubDeliveryUsers(List<UUID> userIds, DriverLoad... loads) {
            given(userClient.getHubDeliveryUserIds(request.getDepartureHubId()))
                    .willReturn(ApiResponse.ok("허브 배송 담당자 목록을 조회했습니다.", userIds));
            Map<UUID, DriverLoad> loadMap = new java.util.HashMap<>();
            for (DriverLoad load : loads) {
                loadMap.put(load.userId(), load);
            }
            given(deliveryRepository.findDriverLoads(userIds)).willReturn(loadMap);
        }

        private DeliveryRoute captureSavedRoute() {
            ArgumentCaptor<DeliveryRoute> routeCaptor = ArgumentCaptor.forClass(DeliveryRoute.class);
            verify(deliveryRouteRepository).save(routeCaptor.capture());
            return routeCaptor.getValue();
        }

        private FeignException hubError(int status) {
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/hubs/routes", Map.of(),
                    null, StandardCharsets.UTF_8, null);
            return FeignException.errorStatus("HubClient#getRoute",
                    feign.Response.builder().status(status).request(feignRequest).headers(Map.of()).build());
        }

        @Test
        @DisplayName("새 주문이면 배송과 배송 경로를 저장")
        void createNew() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            givenHubRoute(new BigDecimal("123.45"), 95);
            givenHubDeliveryUsers(List.of(deliveryUserId));

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
        @DisplayName("허브 서비스에서 받은 거리, 소요시간을 배송 경로의 예상값으로 저장")
        void saveEstimatedValuesFromHub() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            givenHubRoute(new BigDecimal("123.45"), 95);
            givenHubDeliveryUsers(List.of(deliveryUserId));

            deliveryService.createDelivery(request);

            ArgumentCaptor<DeliveryRoute> routeCaptor = ArgumentCaptor.forClass(DeliveryRoute.class);
            verify(deliveryRouteRepository).save(routeCaptor.capture());
            DeliveryRoute savedRoute = routeCaptor.getValue();
            assertThat(savedRoute.getEstimatedDistance()).isEqualByComparingTo("123.45");
            assertThat(savedRoute.getEstimatedDuration()).isEqualTo(95);
            assertThat(savedRoute.getDepartureHubId()).isEqualTo(request.getDepartureHubId());
            assertThat(savedRoute.getArrivalHubId()).isEqualTo(request.getArrivalHubId());
        }

        @Test
        @DisplayName("허브 경로가 없으면(404) HUB_ROUTE_NOT_FOUND, 배송도 저장하지 않음")
        void hubRouteNotFound() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            given(hubClient.getRoute(any(), any())).willThrow(hubError(404));

            assertThatThrownBy(() -> deliveryService.createDelivery(request))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HUB_ROUTE_NOT_FOUND);
            verify(deliveryRepository, never()).save(any());
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("허브 서비스 오류면 HUB_SERVICE_UNAVAILABLE, 배송도 저장하지 않음")
        void hubServiceError() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            given(hubClient.getRoute(any(), any())).willThrow(hubError(500));

            assertThatThrownBy(() -> deliveryService.createDelivery(request))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HUB_SERVICE_UNAVAILABLE);
            verify(deliveryRepository, never()).save(any());
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("허브 응답에 data 가 없으면 HUB_ROUTE_NOT_FOUND")
        void hubResponseWithoutData() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            given(hubClient.getRoute(any(), any())).willReturn(ApiResponse.ok("이동 경로 조회 성공", null));

            assertThatThrownBy(() -> deliveryService.createDelivery(request))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HUB_ROUTE_NOT_FOUND);
            verify(deliveryRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 배송이 있는 주문이면 저장하지 않고 기존 배송 반환")
        void alreadyExists() {
            given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.of(delivery));

            DeliveryResponse response = deliveryService.createDelivery(request);

            assertThat(response.getId()).isEqualTo(deliveryId);
            verify(deliveryRepository, never()).save(any());
            verifyNoInteractions(deliveryRouteRepository, hubClient, userClient);
        }

        @Nested
        @DisplayName("배송 담당자 시스템 배정")
        class AutoAssign {

            private final UUID driverA = UUID.randomUUID();
            private final UUID driverB = UUID.randomUUID();
            private final UUID driverC = UUID.randomUUID();
            private final LocalDateTime now = LocalDateTime.now();

            @BeforeEach
            void setUp() {
                given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.empty());
                givenHubRoute(BigDecimal.TEN, 30);
            }

            @Test
            @DisplayName("출발 허브의 배송 담당자 목록으로 조회")
            void queryByDepartureHub() {
                givenHubDeliveryUsers(List.of(driverA));

                deliveryService.createDelivery(request);

                verify(userClient).getHubDeliveryUserIds(request.getDepartureHubId());
            }

            @Test
            @DisplayName("진행 중인 배송이 가장 적은 담당자를 배정")
            void leastActiveDeliveries() {
                givenHubDeliveryUsers(List.of(driverA, driverB, driverC),
                        new DriverLoad(driverA, 3, now.minusHours(5)),
                        new DriverLoad(driverB, 1, now.minusMinutes(1)),
                        new DriverLoad(driverC, 2, now.minusHours(10)));

                DeliveryResponse response = deliveryService.createDelivery(request);

                assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
            }

            @Test
            @DisplayName("진행 중인 배송 수가 같으면 마지막 배정이 더 오래된 담당자를 배정")
            void tieBreakByOldestLastDelivery() {
                givenHubDeliveryUsers(List.of(driverA, driverB),
                        new DriverLoad(driverA, 2, now.minusMinutes(10)),
                        new DriverLoad(driverB, 2, now.minusHours(3)));

                DeliveryResponse response = deliveryService.createDelivery(request);

                assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
            }

            @Test
            @DisplayName("배송 이력이 없는 담당자가 있으면 그 담당자를 우선 배정")
            void driverWithoutHistoryFirst() {
                // driverC 는 배송 이력이 없어서 findDriverLoads 결과에 없음
                givenHubDeliveryUsers(List.of(driverA, driverB, driverC),
                        new DriverLoad(driverA, 0, now.minusDays(1)),
                        new DriverLoad(driverB, 1, now.minusDays(2)));

                DeliveryResponse response = deliveryService.createDelivery(request);

                assertThat(response.getDeliveryUserId()).isEqualTo(driverC);
            }

            @Test
            @DisplayName("완료된 배송만 있는 담당자(진행 중 0건)는 이력이 없는 담당자보다 뒤")
            void driverWithOnlyFinishedDeliveries() {
                givenHubDeliveryUsers(List.of(driverA, driverB),
                        new DriverLoad(driverA, 0, now.minusDays(1)));

                DeliveryResponse response = deliveryService.createDelivery(request);

                assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
            }

            @Test
            @DisplayName("출발 허브에 배송 담당자가 없으면 미배정으로 생성")
            void noDriverInHub() {
                given(userClient.getHubDeliveryUserIds(request.getDepartureHubId()))
                    .willReturn(ApiResponse.ok("허브 배송 담당자 목록을 조회했습니다.", List.of()));

                DeliveryResponse response = deliveryService.createDelivery(request);

                assertThat(response.getDeliveryUserId()).isNull();
                assertThat(response.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
                assertThat(captureSavedRoute().getDeliveryUserId()).isNull();
                verify(deliveryRepository, never()).findDriverLoads(any());
            }

            @Test
            @DisplayName("user-service 호출이 실패하면 미배정으로 생성")
            void userServiceError() {
                given(userClient.getHubDeliveryUserIds(request.getDepartureHubId())).willThrow(hubError(503));

                DeliveryResponse response = deliveryService.createDelivery(request);

                assertThat(response.getDeliveryUserId()).isNull();
                assertThat(captureSavedRoute().getDeliveryUserId()).isNull();
            }

            @Test
            @DisplayName("배송과 배송 경로에 같은 담당자를 배정")
            void sameDriverOnDeliveryAndRoute() {
                givenHubDeliveryUsers(List.of(driverA, driverB),
                        new DriverLoad(driverA, 1, now.minusHours(1)));

                DeliveryResponse response = deliveryService.createDelivery(request);

                assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
                assertThat(captureSavedRoute().getDeliveryUserId()).isEqualTo(driverB);
            }

            @Nested
            @DisplayName("같은 구간 묶음 배정")
            class Bundle {

                @BeforeEach
                void setUp() {
                    ReflectionTestUtils.setField(deliveryService, "bundleMaxSize", 5);
                }

                // 같은 출발·도착 허브의 HUB_WAITING 배송 수를 담당자별로 넣는다
                private void givenBundleCounts(List<UUID> userIds, Map<UUID, Long> counts) {
                    given(deliveryRepository.countWaitingDeliveriesOnRoute(
                            request.getDepartureHubId(), request.getArrivalHubId(), userIds)).willReturn(counts);
                }

                @Test
                @DisplayName("같은 구간의 출발 전 배송을 맡은 담당자에게 부하와 관계없이 묶어서 배정")
                void bundleToSameRouteDriver() {
                    List<UUID> userIds = List.of(driverA, driverB);
                    givenHubDeliveryUsers(userIds,
                            new DriverLoad(driverA, 4, now.minusMinutes(1)),
                            new DriverLoad(driverB, 0, now.minusDays(1)));
                    givenBundleCounts(userIds, Map.of(driverA, 2L));

                    DeliveryResponse response = deliveryService.createDelivery(request);

                    assertThat(response.getDeliveryUserId()).isEqualTo(driverA);
                    assertThat(captureSavedRoute().getDeliveryUserId()).isEqualTo(driverA);
                }

                @Test
                @DisplayName("묶인 배송이 상한에 도달하면 부하 분산으로 배정")
                void bundleFullFallsBackToLoad() {
                    List<UUID> userIds = List.of(driverA, driverB);
                    givenHubDeliveryUsers(userIds,
                            new DriverLoad(driverA, 5, now.minusMinutes(1)),
                            new DriverLoad(driverB, 1, now.minusDays(1)));
                    givenBundleCounts(userIds, Map.of(driverA, 5L));

                    DeliveryResponse response = deliveryService.createDelivery(request);

                    assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
                }

                @Test
                @DisplayName("부하 분산에서도 그 구간 상한에 도달한 담당자는 진행 중 배송이 가장 적어도 제외")
                void fullDriverExcludedFromLoadBalancing() {
                    List<UUID> userIds = List.of(driverA, driverB);
                    givenHubDeliveryUsers(userIds,
                            new DriverLoad(driverA, 5, now.minusHours(1)),
                            new DriverLoad(driverB, 7, now.minusHours(1)));
                    givenBundleCounts(userIds, Map.of(driverA, 5L));

                    DeliveryResponse response = deliveryService.createDelivery(request);

                    assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
                }

                @Test
                @DisplayName("허브 담당자가 모두 그 구간 상한이면 부하 분산으로 전원 중에서 배정")
                void allDriversFullFallsBackToAll() {
                    List<UUID> userIds = List.of(driverA, driverB);
                    givenHubDeliveryUsers(userIds,
                            new DriverLoad(driverA, 6, now.minusHours(1)),
                            new DriverLoad(driverB, 5, now.minusHours(1)));
                    givenBundleCounts(userIds, Map.of(driverA, 5L, driverB, 5L));

                    DeliveryResponse response = deliveryService.createDelivery(request);

                    assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
                }

                @Test
                @DisplayName("후보가 여럿이면 이미 묶인 배송이 많은 담당자를 배정")
                void preferLargerBundle() {
                    List<UUID> userIds = List.of(driverA, driverB, driverC);
                    givenHubDeliveryUsers(userIds,
                            new DriverLoad(driverA, 1, now.minusHours(1)),
                            new DriverLoad(driverB, 3, now.minusHours(1)));
                    givenBundleCounts(userIds, Map.of(driverA, 1L, driverB, 3L));

                    DeliveryResponse response = deliveryService.createDelivery(request);

                    assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
                }

                @Test
                @DisplayName("묶인 배송 수가 같으면 부하 분산 기준으로 배정")
                void sameBundleSizeTieBreakByLoad() {
                    List<UUID> userIds = List.of(driverA, driverB);
                    givenHubDeliveryUsers(userIds,
                            new DriverLoad(driverA, 3, now.minusHours(1)),
                            new DriverLoad(driverB, 2, now.minusHours(1)));
                    givenBundleCounts(userIds, Map.of(driverA, 2L, driverB, 2L));

                    DeliveryResponse response = deliveryService.createDelivery(request);

                    assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
                }

                @Test
                @DisplayName("같은 구간 배송이 없으면 부하 분산으로 배정")
                void noBundleFallsBackToLoad() {
                    List<UUID> userIds = List.of(driverA, driverB);
                    givenHubDeliveryUsers(userIds,
                            new DriverLoad(driverA, 2, now.minusHours(1)),
                            new DriverLoad(driverB, 1, now.minusHours(1)));
                    givenBundleCounts(userIds, Map.of());

                    DeliveryResponse response = deliveryService.createDelivery(request);

                    assertThat(response.getDeliveryUserId()).isEqualTo(driverB);
                }
            }
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
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 배송이면 404")
        void notFound() {
            given(deliveryRepository.findById(deliveryId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryService.getDelivery(deliveryId, ordererId, RoleType.USER))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_NOT_FOUND);
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
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_NOT_FOUND);
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

            deliveryService.startHubMoving(deliveryId, deliveryUserId, RoleType.USER);

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.MOVING);
        }

        @Test
        @DisplayName("MASTER 는 담당자가 아니어도 상태 변경 가능")
        void startHubMovingByMaster() {
            givenDelivery();
            givenDeliveryRoute();

            deliveryService.startHubMoving(deliveryId, otherUserId, RoleType.MASTER);

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_MOVING);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.MOVING);
            assertThat(deliveryRoute.getDepartedAt()).isNotNull();
        }

        @Test
        @DisplayName("담당자가 아니면 404, 상태 변경 없음")
        void startHubMovingByOther() {
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.startHubMoving(deliveryId, otherUserId, RoleType.USER))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_NOT_FOUND);
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.HUB_WAITING);
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("배송 경로가 없으면 404")
        void startHubMovingWithoutRoute() {
            givenDelivery();
            given(deliveryRouteRepository.findByDeliveryId(deliveryId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryService.startHubMoving(deliveryId, deliveryUserId, RoleType.USER))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_ROUTE_NOT_FOUND);
        }

        @Test
        @DisplayName("목적지 허브 도착 처리하면 배송은 DEST_HUB_ARRIVED, 배송 경로는 ARRIVED + 실제 기록")
        void arriveAtDestinationHub() {
            givenDelivery();
            givenDeliveryRoute();
            delivery.startHubMoving();
            deliveryRoute.startMoving(LocalDateTime.now());

            deliveryService.arriveAtDestinationHub(deliveryId, deliveryUserId, RoleType.USER, new BigDecimal("123.45"));

            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DEST_HUB_ARRIVED);
            assertThat(deliveryRoute.getDeliveryRouteStatus()).isEqualTo(DeliveryRouteStatus.ARRIVED);
            // 실제 이동거리는 입력값, 소요시간은 방금 출발했으므로 0분
            assertThat(deliveryRoute.getActualDistance()).isEqualByComparingTo("123.45");
            assertThat(deliveryRoute.getActualDuration()).isZero();
        }

        @Test
        @DisplayName("업체 배송 시작 -> 완료")
        void startAndCompleteDelivery() {
            givenDelivery();
            delivery.startHubMoving();
            delivery.arrivedHub();

            deliveryService.startDelivery(deliveryId, deliveryUserId, RoleType.USER);
            assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);

            deliveryService.completeDelivery(deliveryId, deliveryUserId, RoleType.USER);
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
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            verifyNoInteractions(deliveryRouteRepository);
        }
    }

    @Nested
    @DisplayName("담당자 배정")
    class AssignDeliveryUserId {

        @BeforeEach
        void setUpUnassigned() {
            // 담당자 배정 전 상태의 배송, 배송 경로
            delivery = Delivery.createDelivery(orderId, ordererId,
                    UUID.randomUUID(), UUID.randomUUID(), "홍길동", "서울시 강남구");
            ReflectionTestUtils.setField(delivery, "id", deliveryId);
            deliveryRoute = DeliveryRoute.createDeliveryRoute(deliveryId, null,
                    delivery.getDepartureHubId(), delivery.getArrivalHubId(), BigDecimal.TEN, 30);
        }

        // 헤더로 받은 허브 id 는 엔티티 값과 다른 인스턴스 (== 비교 버그를 잡기 위해 새로 만든다)
        private UUID departureHubIdFromHeader() {
            return UUID.fromString(delivery.getDepartureHubId().toString());
        }

        @Test
        @DisplayName("요청한 본인이 배송, 배송 경로 담당자로 배정")
        void assignSelf() {
            givenDelivery();
            givenDeliveryRoute();

            DeliveryResponse response = deliveryService.assignDeliveryUserId(deliveryId, deliveryUserId, departureHubIdFromHeader(), AffiliationType.HUB);

            assertThat(response.getDeliveryUserId()).isEqualTo(deliveryUserId);
            assertThat(delivery.getDeliveryUserId()).isEqualTo(deliveryUserId);
            assertThat(deliveryRoute.getDeliveryUserId()).isEqualTo(deliveryUserId);
        }

        @Test
        @DisplayName("HUB 소속이 아니면 403, 조회도 하지 않음")
        void notHubAffiliation() {
            assertThatThrownBy(() -> deliveryService.assignDeliveryUserId(deliveryId, deliveryUserId, UUID.randomUUID(), AffiliationType.COMPANY))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_ACCESS_DENIED);
            verifyNoInteractions(deliveryRepository, deliveryRouteRepository);
        }

        @Test
        @DisplayName("출발 허브 소속이 아니면 403, 배정하지 않음")
        void otherHub() {
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.assignDeliveryUserId(deliveryId, deliveryUserId, UUID.randomUUID(), AffiliationType.HUB))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_HUB_MISMATCH);
            assertThat(delivery.getDeliveryUserId()).isNull();
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("이미 담당자가 있으면 배정 불가")
        void alreadyAssigned() {
            delivery.assignDeliveryUserId(deliveryUserId);
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.assignDeliveryUserId(deliveryId, otherUserId, departureHubIdFromHeader(), AffiliationType.HUB))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_USER_ALREADY_ASSIGNED);
            assertThat(delivery.getDeliveryUserId()).isEqualTo(deliveryUserId);
        }

        @Test
        @DisplayName("허브 대기 상태가 아니면 배정 불가")
        void notWaiting() {
            delivery.cancel();
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.assignDeliveryUserId(deliveryId, deliveryUserId, departureHubIdFromHeader(), AffiliationType.HUB))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
        }
    }

    @Nested
    @DisplayName("미배정 배송 목록 조회")
    class GetUnassignedDeliveries {

        private final UUID hubId = UUID.randomUUID();
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("소속 허브에서 출발하는 HUB_WAITING 미배정 배송을 조회")
        void byHubUser() {
            Delivery unassigned = Delivery.createDelivery(orderId, ordererId, hubId, UUID.randomUUID(), "홍길동", "서울시 강남구");
            given(deliveryRepository.findAllByDeliveryUserIdIsNullAndDeliveryStatusAndDepartureHubId(
                    DeliveryStatus.HUB_WAITING, hubId, pageable))
                    .willReturn(new PageImpl<>(List.of(unassigned), pageable, 1));

            PagedResponse<DeliveryResponse> response = deliveryService.getUnassignedDeliveries(hubId, AffiliationType.HUB, pageable);

            assertThat(response.totalElements()).isEqualTo(1);
            assertThat(response.contents()).extracting(DeliveryResponse::getDeliveryUserId).containsOnlyNulls();
            assertThat(response.contents()).extracting(DeliveryResponse::getDepartureHubId).containsOnly(hubId);
        }

        @Test
        @DisplayName("HUB 소속이 아니면 403, 조회하지 않음")
        void notHubAffiliation() {
            assertThatThrownBy(() -> deliveryService.getUnassignedDeliveries(hubId, AffiliationType.COMPANY, pageable))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_ACCESS_DENIED);
            verifyNoInteractions(deliveryRepository);
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
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_ACCESS_DENIED);
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
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_NOT_FOUND);
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
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DELIVERY_STATUS);
            assertThat(delivery.isDeleted()).isFalse();
            verifyNoInteractions(deliveryRouteRepository);
        }

        @Test
        @DisplayName("주문자도 MASTER 도 아니면 404")
        void byOther() {
            delivery.cancel();
            givenDelivery();

            assertThatThrownBy(() -> deliveryService.deleteDelivery(deliveryId, otherUserId, RoleType.USER))
                    .isInstanceOf(BaseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DELIVERY_NOT_FOUND);
            assertThat(delivery.isDeleted()).isFalse();
        }
    }
}
