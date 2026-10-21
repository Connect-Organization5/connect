package connect.company_service.client.user;

import connect.company_service.client.user.dto.UserResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 업체 관리자 지정 시 대상 사용자가 그 업체 소속인지 User Service API로 확인
@Component
@RequiredArgsConstructor
public class UserValidator {

    private static final String AFFILIATION_COMPANY = "COMPANY";

    private final UserClient userClient;

    public void validateCompanyMember(UUID requesterId, String requesterRole, UUID userId, UUID companyId) {
        UserResponse user;
        try {
            user = userClient.getUser(requesterId, requesterRole, userId).getData();
        } catch (FeignException.NotFound e) {
            // 404만 USER_NOT_FOUND로 변환. 500, 연결 실패 등 다른 Feign 오류는 그대로 전파되어 서버 오류로 처리된다
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }

        if (!AFFILIATION_COMPANY.equals(user.affiliationType()) || !companyId.equals(user.affiliationId())) {
            throw new BaseException(ErrorCode.COMPANY_MANAGER_NOT_MEMBER);
        }
    }
}
