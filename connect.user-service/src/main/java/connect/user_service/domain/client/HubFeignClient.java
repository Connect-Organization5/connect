package connect.user_service.domain.client;

import connect.user_service.domain.client.Api.HubFeignApi;
import connect.user_service.domain.client.clientinterface.HubClient;
import connect.user_service.domain.user.dto.response.HubInfo;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubFeignClient implements HubClient {
    private final HubFeignApi hubFeignApi;

    // 존재하지 않는 Hub 면 Hub Service 가 404 → Feign 은 null 이 아니라 FeignException.NotFound 를 던짐
    @Override
    public HubInfo getHub(UUID hubId) {
        try {
            return hubFeignApi.getHub(hubId).getData();
        } catch (FeignException.NotFound e) {
            // 404 만 HUB_NOT_FOUND 로 변환. 500, 연결 실패 등 다른 오류는 그대로 던져서 서버 오류로 처리
            throw new BaseException(ErrorCode.HUB_NOT_FOUND);
        }
    }

}