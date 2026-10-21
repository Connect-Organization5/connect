package connect.delivery_service.domain.service;

import connect.delivery_service.client.hub.HubClient;
import connect.delivery_service.client.hub.HubRouteResponse;
import connect.delivery_service.client.user.UserClient;
import connect.delivery_service.domain.dto.CreateDeliveryRequest;
import connect.delivery_service.domain.entity.Delivery;
import connect.delivery_service.domain.entity.DeliveryStatus;
import connect.delivery_service.domain.repository.DeliveryRepository;
import connect.delivery_service.domain.repository.DeliveryRepositoryCustomImpl.DriverLoad;
import connect.delivery_service.domain.repository.DeliveryRouteRepository;
import connect.delivery_service.global.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 자동 배정 방식별 운행 효율 시뮬레이션
// - 실제 DeliveryService.createDelivery 를 호출하고, Repository·외부 서비스만 메모리 구현으로 대신한다
// - 가정: 라운드가 끝나면 담당자가 맡은 출발 전 배송을 구간(출발·도착 허브)별로 한 번에 싣고 출발한다
//   → (담당자, 구간) 묶음 1개 = 운행 1회. 출발한 배송은 다음 라운드 전에 완료된다
// - bundleMaxSize = 0 이면 묶음 배정이 꺼진 기존 부하 분산과 같다
// - 같은 seed 로 같은 주문을 만들어 방식만 바꿔 비교한다
class DeliveryAssignmentSimulationTest {

    private static final long SEED = 42L;
    private static final int DEPARTURE_HUBS = 3;
    private static final int DRIVERS_PER_HUB = 4;
    // 도착 허브별 주문 비중. 실제 수요처럼 일부 지역에 주문이 몰리도록 치우치게 둔다
    private static final double[] ARRIVAL_WEIGHTS = {0.35, 0.25, 0.15, 0.10, 0.10, 0.05};
    private static final int ROUNDS = 50;
    private static final int ORDERS_PER_ROUND = 24;

    private static final int BASELINE = 0;
    private static final int DEFAULT_BUNDLE_MAX_SIZE = 5;

    private static final Set<DeliveryStatus> ACTIVE = EnumSet.of(
            DeliveryStatus.HUB_WAITING, DeliveryStatus.HUB_MOVING,
            DeliveryStatus.DEST_HUB_ARRIVED, DeliveryStatus.OUT_FOR_DELIVERY);

    private record Route(UUID departureHubId, UUID arrivalHubId) {}

    private record Trip(UUID deliveryUserId, Route route) {}

    // trips: 운행 수, distance: 운행 수 × 구간 거리 합(km), maxGroupSize: 한 운행에 실린 최대 배송 수
    // imbalance: 라운드마다 허브 안 담당자별 배정 건수의 (최대 - 최소)
    private record Result(int bundleMaxSize, int trips, BigDecimal distance,
                          int maxGroupSize, int maxImbalance, double avgImbalance) {}

    // 허브·담당자·구간 거리·라운드별 주문. seed 가 같으면 항상 같은 시나리오
    private static class Scenario {
        final List<UUID> departureHubs = new ArrayList<>();
        final List<UUID> arrivalHubs = new ArrayList<>();
        final Map<UUID, List<UUID>> driversByHub = new HashMap<>();
        final Map<Route, BigDecimal> distances = new HashMap<>();
        final List<List<Route>> ordersByRound = new ArrayList<>();

        Scenario(long seed) {
            Random random = new Random(seed);
            for (int i = 0; i < DEPARTURE_HUBS; i++) {
                UUID hubId = new UUID(random.nextLong(), random.nextLong());
                departureHubs.add(hubId);
                List<UUID> drivers = new ArrayList<>();
                for (int j = 0; j < DRIVERS_PER_HUB; j++) {
                    drivers.add(new UUID(random.nextLong(), random.nextLong()));
                }
                driversByHub.put(hubId, drivers);
            }
            for (int i = 0; i < ARRIVAL_WEIGHTS.length; i++) {
                arrivalHubs.add(new UUID(random.nextLong(), random.nextLong()));
            }
            for (UUID departure : departureHubs) {
                for (UUID arrival : arrivalHubs) {
                    distances.put(new Route(departure, arrival), BigDecimal.valueOf(30 + random.nextInt(271)));
                }
            }
            for (int round = 0; round < ROUNDS; round++) {
                List<Route> orders = new ArrayList<>();
                for (int i = 0; i < ORDERS_PER_ROUND; i++) {
                    UUID departure = departureHubs.get(random.nextInt(departureHubs.size()));
                    orders.add(new Route(departure, arrivalHubs.get(pickArrival(random.nextDouble()))));
                }
                ordersByRound.add(orders);
            }
        }

        private static int pickArrival(double r) {
            double cumulative = 0;
            for (int i = 0; i < ARRIVAL_WEIGHTS.length; i++) {
                cumulative += ARRIVAL_WEIGHTS[i];
                if (r < cumulative) {
                    return i;
                }
            }
            return ARRIVAL_WEIGHTS.length - 1;
        }
    }

    private Result simulate(Scenario scenario, int bundleMaxSize) {
        List<Delivery> store = new ArrayList<>();
        LocalDateTime clock = LocalDateTime.of(2026, 1, 1, 9, 0);

        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        given(deliveryRepository.findByOrderId(any())).willReturn(Optional.empty());
        given(deliveryRepository.save(any(Delivery.class))).willAnswer(inv -> {
            Delivery delivery = inv.getArgument(0);
            // id 는 DB 가 채우는 값이라 직접 넣는다
            ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
            store.add(delivery);
            return delivery;
        });
        given(deliveryRepository.findDriverLoads(anyList()))
                .willAnswer(inv -> driverLoads(store, inv.getArgument(0)));
        given(deliveryRepository.countWaitingDeliveriesOnRoute(any(), any(), anyList()))
                .willAnswer(inv -> waitingCounts(store, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        HubClient hubClient = mock(HubClient.class);
        given(hubClient.getRoute(any(), any())).willAnswer(inv -> {
            Route route = new Route(inv.getArgument(0), inv.getArgument(1));
            return ApiResponse.ok("이동 경로 조회 성공", new HubRouteResponse(UUID.randomUUID(),
                    route.departureHubId(), route.arrivalHubId(), scenario.distances.get(route), 60));
        });

        UserClient userClient = mock(UserClient.class);
        given(userClient.getHubDeliveryUserIds(any())).willAnswer(inv ->
                ApiResponse.ok("허브 배송 담당자 목록을 조회했습니다.", scenario.driversByHub.get(inv.<UUID>getArgument(0))));

        DeliveryService deliveryService = new DeliveryService(
                deliveryRepository, mock(DeliveryRouteRepository.class), hubClient, userClient);
        ReflectionTestUtils.setField(deliveryService, "bundleMaxSize", bundleMaxSize);

        int trips = 0;
        int maxGroupSize = 0;
        int maxImbalance = 0;
        long imbalanceSum = 0;
        BigDecimal distance = BigDecimal.ZERO;

        for (List<Route> orders : scenario.ordersByRound) {
            Map<UUID, Integer> assignedThisRound = new HashMap<>();
            for (Route route : orders) {
                UUID assigned = deliveryService.createDelivery(request(route)).getDeliveryUserId();
                assignedThisRound.merge(assigned, 1, Integer::sum);
                // assignedAt 은 LocalDateTime.now() 라 같은 밀리초에 겹칠 수 있어, 결과가 실행마다 달라지지 않도록 가상 시계로 덮어쓴다
                ReflectionTestUtils.setField(store.get(store.size() - 1), "assignedAt", clock.plusMinutes(store.size()));
            }

            // 라운드 종료: 출발 전 배송을 (담당자, 구간) 단위로 묶어 운행
            Map<Trip, Long> groups = store.stream()
                    .filter(d -> d.getDeliveryStatus() == DeliveryStatus.HUB_WAITING)
                    .collect(Collectors.groupingBy(
                            d -> new Trip(d.getDeliveryUserId(), new Route(d.getDepartureHubId(), d.getArrivalHubId())),
                            Collectors.counting()));
            trips += groups.size();
            for (Map.Entry<Trip, Long> group : groups.entrySet()) {
                distance = distance.add(scenario.distances.get(group.getKey().route()));
                maxGroupSize = Math.max(maxGroupSize, group.getValue().intValue());
            }
            store.stream()
                    .filter(d -> d.getDeliveryStatus() == DeliveryStatus.HUB_WAITING)
                    .forEach(d -> ReflectionTestUtils.setField(d, "deliveryStatus", DeliveryStatus.DELIVERED));

            for (List<UUID> drivers : scenario.driversByHub.values()) {
                List<Integer> counts = drivers.stream().map(id -> assignedThisRound.getOrDefault(id, 0)).toList();
                int imbalance = counts.stream().max(Integer::compare).orElse(0) - counts.stream().min(Integer::compare).orElse(0);
                maxImbalance = Math.max(maxImbalance, imbalance);
                imbalanceSum += imbalance;
            }
        }

        double avgImbalance = (double) imbalanceSum / (ROUNDS * DEPARTURE_HUBS);
        return new Result(bundleMaxSize, trips, distance, maxGroupSize, maxImbalance, avgImbalance);
    }

    private static CreateDeliveryRequest request(Route route) {
        CreateDeliveryRequest request = new CreateDeliveryRequest();
        ReflectionTestUtils.setField(request, "orderId", UUID.randomUUID());
        ReflectionTestUtils.setField(request, "ordererId", UUID.randomUUID());
        ReflectionTestUtils.setField(request, "departureHubId", route.departureHubId());
        ReflectionTestUtils.setField(request, "arrivalHubId", route.arrivalHubId());
        ReflectionTestUtils.setField(request, "receiver", "홍길동");
        ReflectionTestUtils.setField(request, "deliveryAddress", "서울시 강남구");
        return request;
    }

    // DeliveryRepositoryCustomImpl.findDriverLoads 와 같은 규칙: 담당자별 진행 중 배송 수, 마지막 배정 시각
    private static Map<UUID, DriverLoad> driverLoads(List<Delivery> store, List<UUID> userIds) {
        return store.stream()
                .filter(d -> d.getDeliveryUserId() != null && userIds.contains(d.getDeliveryUserId()))
                .collect(Collectors.groupingBy(Delivery::getDeliveryUserId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new DriverLoad(
                        e.getKey(),
                        e.getValue().stream().filter(d -> ACTIVE.contains(d.getDeliveryStatus())).count(),
                        e.getValue().stream().map(Delivery::getAssignedAt).filter(Objects::nonNull)
                                .max(LocalDateTime::compareTo).orElse(null))));
    }

    // DeliveryRepositoryCustomImpl.countWaitingDeliveriesOnRoute 와 같은 규칙
    private static Map<UUID, Long> waitingCounts(List<Delivery> store, UUID departureHubId, UUID arrivalHubId, List<UUID> userIds) {
        return store.stream()
                .filter(d -> d.getDeliveryStatus() == DeliveryStatus.HUB_WAITING)
                .filter(d -> d.getDepartureHubId().equals(departureHubId) && d.getArrivalHubId().equals(arrivalHubId))
                .filter(d -> d.getDeliveryUserId() != null && userIds.contains(d.getDeliveryUserId()))
                .collect(Collectors.groupingBy(Delivery::getDeliveryUserId, Collectors.counting()));
    }

    private static void print(List<Result> results) {
        Result baseline = results.get(0);
        System.out.printf("%n[자동 배정 시뮬레이션] seed=%d, 라운드 %d × 주문 %d건, 출발 허브 %d곳 × 담당자 %d명, 도착 허브 %d곳%n",
                SEED, ROUNDS, ORDERS_PER_ROUND, DEPARTURE_HUBS, DRIVERS_PER_HUB, ARRIVAL_WEIGHTS.length);
        System.out.println("| 묶음 상한 | 운행 수 | 운행 감소 | 총 이동거리(km) | 운행당 최대 배송 | 부하 편차(최대) | 부하 편차(평균) |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (Result r : results) {
            String label = r.bundleMaxSize() == BASELINE ? "0 (기존)"
                    : r.bundleMaxSize() == Integer.MAX_VALUE ? "무제한" : String.valueOf(r.bundleMaxSize());
            double reduction = 100.0 * (baseline.trips() - r.trips()) / baseline.trips();
            System.out.printf("| %s | %d | %.1f%% | %s | %d | %d | %.2f |%n", label, r.trips(), reduction,
                    r.distance().toPlainString(), r.maxGroupSize(), r.maxImbalance(), r.avgImbalance());
        }
    }

    @Test
    @DisplayName("묶음 배정(상한 5)은 기존 부하 분산보다 운행 수와 총 이동거리가 줄어든다")
    void bundlingReducesTripsAndDistance() {
        Scenario scenario = new Scenario(SEED);
        Result baseline = simulate(scenario, BASELINE);
        Result bundled = simulate(scenario, DEFAULT_BUNDLE_MAX_SIZE);

        assertThat(bundled.trips()).isLessThan(baseline.trips());
        assertThat(bundled.distance()).isLessThan(baseline.distance());
    }

    @Test
    @DisplayName("한 운행에 묶이는 배송 수는 상한을 넘지 않는다")
    void groupSizeWithinCap() {
        Scenario scenario = new Scenario(SEED);
        for (int cap : new int[]{2, 3, 5, 10}) {
            assertThat(simulate(scenario, cap).maxGroupSize()).as("상한 %d", cap).isLessThanOrEqualTo(cap);
        }
    }

    @Test
    @DisplayName("묶음 상한별 운행 수·이동거리·부하 편차 비교표 출력")
    void compareByCap() {
        Scenario scenario = new Scenario(SEED);
        List<Result> results = new ArrayList<>();
        for (int cap : new int[]{BASELINE, 1, 3, 5, 10, Integer.MAX_VALUE}) {
            results.add(simulate(scenario, cap));
        }
        print(results);

        // 상한을 늘릴수록 운행 수는 늘지 않는다 (더 많이 묶을 수 있으므로)
        for (int i = 2; i < results.size(); i++) {
            assertThat(results.get(i).trips()).as("상한 %d", results.get(i).bundleMaxSize())
                    .isLessThanOrEqualTo(results.get(i - 1).trips());
        }
    }
}
