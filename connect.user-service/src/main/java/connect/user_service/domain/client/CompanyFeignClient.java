package connect.user_service.domain.client;

import connect.user_service.domain.client.Api.CompanyFeignApi;
import connect.user_service.domain.client.clientinterface.CompanyClient;
import connect.user_service.domain.user.dto.response.CompanyInfo;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CompanyFeignClient implements CompanyClient {
    private final CompanyFeignApi companyFeignApi;

    // 존재하지 않는 Company 면 Company Service 가 404 → Feign 은 null 이 아니라 FeignException.NotFound 를 던짐
    @Override
    public CompanyInfo getCompany(UUID companyId) {
        try {
            return companyFeignApi.getCompany(companyId).getData();
        } catch (FeignException.NotFound e) {
            // 404 만 COMPANY_NOT_FOUND 로 변환. 500, 연결 실패 등 다른 오류는 그대로 던져서 서버 오류로 처리
            throw new BaseException(ErrorCode.COMPANY_NOT_FOUND);
        }
    }
}
