package connect.company_service.client.hub;

import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Company 생성/수정 시 hubId가 실제 Hub인지 Hub Service API로 확인
@Component
@RequiredArgsConstructor
public class HubValidator {

    private final HubClient hubClient;

    public void validateHubExists(UUID hubId) {
        try {
            hubClient.getHub(hubId);
        } catch (FeignException.NotFound e) {
            // 404만 HUB_NOT_FOUND로 변환. 500, 연결 실패 등 다른 Feign 오류는 그대로 전파되어 서버 오류로 처리된다
            throw new BaseException(ErrorCode.HUB_NOT_FOUND);
        }
    }
}
