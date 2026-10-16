package connect.company_service.client.hub;

import connect.company_service.client.hub.dto.HubResponse;
import connect.company_service.global.dto.ApiResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HubValidatorTest {

    @Mock
    private HubClient hubClient;

    @InjectMocks
    private HubValidator hubValidator;

    private final UUID hubId = UUID.randomUUID();

    private final Request request = Request.create(
            Request.HttpMethod.GET, "/hubs/" + hubId, Map.of(), null, StandardCharsets.UTF_8, null);

    @Test
    void Hub가_존재하면_예외가_발생하지_않는다() {
        given(hubClient.getHub(hubId))
                .willReturn(ApiResponse.ok("허브 조회 성공", new HubResponse(hubId, "서울특별시 센터", "서울특별시 송파구 송파대로 55")));

        assertThatCode(() -> hubValidator.validateHubExists(hubId))
                .doesNotThrowAnyException();
    }

    @Test
    void Hub_Service가_404를_반환하면_HUB_NOT_FOUND() {
        given(hubClient.getHub(hubId))
                .willThrow(new FeignException.NotFound("Not Found", request, null, null));

        assertThatThrownBy(() -> hubValidator.validateHubExists(hubId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.HUB_NOT_FOUND.getMessage());
    }

    @Test
    void Hub_Service가_500을_반환하면_HUB_NOT_FOUND로_변환하지_않는다() {
        given(hubClient.getHub(hubId))
                .willThrow(new FeignException.InternalServerError("Internal Server Error", request, null, null));

        assertThatThrownBy(() -> hubValidator.validateHubExists(hubId))
                .isInstanceOf(FeignException.InternalServerError.class)
                .isNotInstanceOf(BaseException.class);
    }

    @Test
    void Hub_Service_연결에_실패하면_HUB_NOT_FOUND로_변환하지_않는다() {
        given(hubClient.getHub(hubId))
                .willThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request));

        assertThatThrownBy(() -> hubValidator.validateHubExists(hubId))
                .isInstanceOf(RetryableException.class)
                .isNotInstanceOf(BaseException.class);
    }
}
