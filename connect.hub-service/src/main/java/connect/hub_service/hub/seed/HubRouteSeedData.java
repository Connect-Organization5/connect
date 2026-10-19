package connect.hub_service.hub.seed;

import connect.hub_service.hub.seed.HubSeedData.HubSeed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 허브 간 P2P 경로 초기 데이터 정의. DB에 접근하지 않는다.
 *
 * <p><b>distance·duration은 실제 도로 측정값이 아닌 추정값이다.</b>
 * 허브 주소를 기준으로 정한 대표 좌표(근사값)로 Haversine 직선거리를 구하고,
 * 도로 우회 보정계수 1.3을 곱해 distance(km)를, 평균 속도 80km/h를 가정해 duration(분)을 계산한다.
 * 도착 예상 시각이나 내비게이션 수준의 정확한 값으로 사용하지 않는다.
 *
 * <p>서로 다른 허브 쌍(17C2 = 136)마다 값을 한 번만 계산하고, A→B와 B→A를 같은 값의 별도 경로로 만든다. (총 272건)
 */
public final class HubRouteSeedData {

    public record Coordinate(double latitude, double longitude) {
    }

    public record RouteSeed(UUID id, UUID departureHubId, UUID arrivalHubId, double distance, int duration) {
    }

    static final double DETOUR_FACTOR = 1.3;
    static final double AVERAGE_SPEED_KMH = 80.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    // 허브 주소 기준 대표 좌표(근사값). 키는 HubSeedData의 허브명.
    private static final Map<String, Coordinate> COORDINATES = Map.ofEntries(
            Map.entry("서울특별시 센터", new Coordinate(37.5145, 127.1059)),
            Map.entry("경기 북부 센터", new Coordinate(37.6584, 126.8320)),
            Map.entry("경기 남부 센터", new Coordinate(37.1940, 127.3800)),
            Map.entry("부산광역시 센터", new Coordinate(35.1150, 129.0400)),
            Map.entry("대구광역시 센터", new Coordinate(35.8760, 128.5960)),
            Map.entry("인천광역시 센터", new Coordinate(37.4563, 126.7052)),
            Map.entry("광주광역시 센터", new Coordinate(35.1600, 126.8510)),
            Map.entry("대전광역시 센터", new Coordinate(36.3504, 127.3845)),
            Map.entry("울산광역시 센터", new Coordinate(35.5384, 129.3114)),
            Map.entry("세종특별자치시 센터", new Coordinate(36.4800, 127.2890)),
            Map.entry("강원특별자치도 센터", new Coordinate(37.8813, 127.7298)),
            Map.entry("충청북도 센터", new Coordinate(36.6424, 127.4890)),
            Map.entry("충청남도 센터", new Coordinate(36.6580, 126.6730)),
            Map.entry("전북특별자치도 센터", new Coordinate(35.8190, 127.1080)),
            Map.entry("전라남도 센터", new Coordinate(34.8170, 126.4650)),
            Map.entry("경상북도 센터", new Coordinate(36.5750, 128.5080)),
            Map.entry("경상남도 센터", new Coordinate(35.2270, 128.6810))
    );

    private static final List<RouteSeed> ROUTES = build();

    private HubRouteSeedData() {
    }

    public static List<RouteSeed> routes() {
        return ROUTES;
    }

    public static Coordinate coordinate(String hubName) {
        return COORDINATES.get(hubName);
    }

    // 추정 거리(km): Haversine 직선거리 x 우회 보정계수, 소수 첫째 자리 반올림
    static double estimateDistance(Coordinate a, Coordinate b) {
        return Math.round(haversineKm(a, b) * DETOUR_FACTOR * 10) / 10.0;
    }

    // 추정 소요시간(분): 거리 / 평균 속도, 분 단위 반올림
    static int estimateDuration(double distanceKm) {
        return (int) Math.round(distanceKm / AVERAGE_SPEED_KMH * 60);
    }

    static double haversineKm(Coordinate a, Coordinate b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    private static List<RouteSeed> build() {
        List<HubSeed> hubs = HubSeedData.hubs();
        List<RouteSeed> routes = new ArrayList<>();
        for (int i = 0; i < hubs.size(); i++) {
            for (int j = i + 1; j < hubs.size(); j++) {
                HubSeed a = hubs.get(i);
                HubSeed b = hubs.get(j);
                double distance = estimateDistance(COORDINATES.get(a.name()), COORDINATES.get(b.name()));
                int duration = estimateDuration(distance);
                routes.add(route(a, b, distance, duration));
                routes.add(route(b, a, distance, duration));
            }
        }
        return List.copyOf(routes);
    }

    private static RouteSeed route(HubSeed from, HubSeed to, double distance, int duration) {
        UUID id = UUID.nameUUIDFromBytes(("route:" + from.id() + ":" + to.id()).getBytes(StandardCharsets.UTF_8));
        return new RouteSeed(id, from.id(), to.id(), distance, duration);
    }
}
