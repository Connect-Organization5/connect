package connect.hub_service.hub.seed;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 허브 17개 초기 데이터 정의. DB에 접근하지 않는다.
 * ID는 이름 기반 고정 UUID라 어느 환경에서 생성해도 같다. (Company 등이 hubId를 저장하기 때문)
 */
public final class HubSeedData {

    public record HubSeed(UUID id, String name, String address) {
    }

    private static final List<HubSeed> HUBS = List.of(
            hub("서울특별시 센터", "서울특별시 송파구 송파대로 55"),
            hub("경기 북부 센터", "경기도 고양시 덕양구 권율대로 570"),
            hub("경기 남부 센터", "경기도 이천시 덕평로 257-21"),
            hub("부산광역시 센터", "부산 동구 중앙대로 206"),
            hub("대구광역시 센터", "대구 북구 태평로 161"),
            hub("인천광역시 센터", "인천 남동구 정각로 29"),
            hub("광주광역시 센터", "광주 서구 내방로 111"),
            hub("대전광역시 센터", "대전 서구 둔산로 100"),
            hub("울산광역시 센터", "울산 남구 중앙로 201"),
            hub("세종특별자치시 센터", "세종특별자치시 한누리대로 2130"),
            hub("강원특별자치도 센터", "강원특별자치도 춘천시 중앙로 1"),
            hub("충청북도 센터", "충북 청주시 상당구 상당로 82"),
            hub("충청남도 센터", "충남 홍성군 홍북읍 충남대로 21"),
            hub("전북특별자치도 센터", "전북특별자치도 전주시 완산구 효자로 225"),
            hub("전라남도 센터", "전남 무안군 삼향읍 오룡길 1"),
            hub("경상북도 센터", "경북 안동시 풍천면 도청대로 455"),
            hub("경상남도 센터", "경남 창원시 의창구 중앙대로 300")
    );

    private HubSeedData() {
    }

    public static List<HubSeed> hubs() {
        return HUBS;
    }

    private static HubSeed hub(String name, String address) {
        UUID id = UUID.nameUUIDFromBytes(("hub:" + name).getBytes(StandardCharsets.UTF_8));
        return new HubSeed(id, name, address);
    }
}
