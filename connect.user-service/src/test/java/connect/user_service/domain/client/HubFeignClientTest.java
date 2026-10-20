package connect.user_service.domain.client;

import connect.user_service.domain.client.Api.HubFeignApi;
import connect.user_service.domain.user.dto.response.HubInfo;
import connect.user_service.global.dto.ApiResponse;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HubFeignClientTest {

    private static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");

    // FeignException 생성에 필요한 요청 정보 (실제로 보내지 않음)
    private static final Request REQUEST = Request.create(
            Request.HttpMethod.GET, "/hubs/" + HUB_ID, Map.of(), null, StandardCharsets.UTF_8, null);

    @Mock
    private HubFeignApi hubFeignApi;

    @InjectMocks
    private HubFeignClient hubFeignClient;

    @Test
    @DisplayName("Hub 가 존재하면 응답의 data 를 반환")
    void getHub_success() {
        // given
        HubInfo hub = new HubInfo(HUB_ID, "서울특별시 센터", "서울특별시 송파구 송파대로 55");
        given(hubFeignApi.getHub(HUB_ID)).willReturn(ApiResponse.ok("허브 조회 성공", hub));

        // when
        HubInfo result = hubFeignClient.getHub(HUB_ID);

        // then
        assertThat(result).isEqualTo(hub);
    }

    @Test
    @DisplayName("Hub Service 가 404 를 반환하면 HUB_NOT_FOUND")
    void getHub_notFound() {
        // given
        given(hubFeignApi.getHub(HUB_ID)).willThrow(new FeignException.NotFound("Not Found", REQUEST, null, null));

        // when & then
        assertThatThrownBy(() -> hubFeignClient.getHub(HUB_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HUB_NOT_FOUND);
    }

    @Test
    @DisplayName("Hub Service 가 500 을 반환하면 HUB_NOT_FOUND 로 바꾸지 않고 그대로 던짐")
    void getHub_serverError() {
        // given
        given(hubFeignApi.getHub(HUB_ID))
                .willThrow(new FeignException.InternalServerError("Internal Server Error", REQUEST, null, null));

        // when & then
        assertThatThrownBy(() -> hubFeignClient.getHub(HUB_ID))
                .isInstanceOf(FeignException.InternalServerError.class);
    }

    @Test
    @DisplayName("Hub Service 연결에 실패하면 HUB_NOT_FOUND 로 바꾸지 않고 그대로 던짐")
    void getHub_connectionFailed() {
        // given
        given(hubFeignApi.getHub(HUB_ID))
                .willThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, REQUEST));

        // when & then
        assertThatThrownBy(() -> hubFeignClient.getHub(HUB_ID))
                .isInstanceOf(RetryableException.class);
    }
}
