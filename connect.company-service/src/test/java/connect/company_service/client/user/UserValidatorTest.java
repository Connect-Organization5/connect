package connect.company_service.client.user;

import connect.company_service.client.user.dto.UserResponse;
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
class UserValidatorTest {

    private static final String ROLE_MASTER = "MASTER";

    @Mock
    private UserClient userClient;

    @InjectMocks
    private UserValidator userValidator;

    private final UUID masterId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    private final Request request = Request.create(
            Request.HttpMethod.GET, "/internal/users/" + userId, Map.of(), null, StandardCharsets.UTF_8, null);

    private void givenUser(String affiliationType, UUID affiliationId) {
        given(userClient.getUser(masterId, ROLE_MASTER, userId))
                .willReturn(ApiResponse.ok("회원 정보를 조회했습니다.", new UserResponse(userId, affiliationType, affiliationId)));
    }

    @Test
    void 사용자가_해당_업체_소속이면_예외가_발생하지_않는다() {
        givenUser("COMPANY", companyId);

        assertThatCode(() -> userValidator.validateCompanyMember(masterId, ROLE_MASTER, userId, companyId))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른_업체_소속이면_COMPANY_MANAGER_NOT_MEMBER() {
        givenUser("COMPANY", UUID.randomUUID());

        assertThatThrownBy(() -> userValidator.validateCompanyMember(masterId, ROLE_MASTER, userId, companyId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.COMPANY_MANAGER_NOT_MEMBER.getMessage());
    }

    @Test
    void 허브_소속이면_affiliationId가_같아도_COMPANY_MANAGER_NOT_MEMBER() {
        givenUser("HUB", companyId);

        assertThatThrownBy(() -> userValidator.validateCompanyMember(masterId, ROLE_MASTER, userId, companyId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.COMPANY_MANAGER_NOT_MEMBER.getMessage());
    }

    @Test
    void User_Service가_404를_반환하면_USER_NOT_FOUND() {
        given(userClient.getUser(masterId, ROLE_MASTER, userId))
                .willThrow(new FeignException.NotFound("Not Found", request, null, null));

        assertThatThrownBy(() -> userValidator.validateCompanyMember(masterId, ROLE_MASTER, userId, companyId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void User_Service_연결에_실패하면_USER_NOT_FOUND로_변환하지_않는다() {
        given(userClient.getUser(masterId, ROLE_MASTER, userId))
                .willThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request));

        assertThatThrownBy(() -> userValidator.validateCompanyMember(masterId, ROLE_MASTER, userId, companyId))
                .isInstanceOf(RetryableException.class)
                .isNotInstanceOf(BaseException.class);
    }
}
