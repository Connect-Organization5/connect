package connect.user_service.domain.client;

import connect.user_service.domain.client.Api.CompanyFeignApi;
import connect.user_service.domain.user.dto.response.CompanyInfo;
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
class CompanyFeignClientTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");
    private static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-000000002000");

    // FeignException 생성에 필요한 요청 정보 (실제로 보내지 않음)
    private static final Request REQUEST = Request.create(
            Request.HttpMethod.GET, "/companies/" + COMPANY_ID, Map.of(), null, StandardCharsets.UTF_8, null);

    @Mock
    private CompanyFeignApi companyFeignApi;

    @InjectMocks
    private CompanyFeignClient companyFeignClient;

    @Test
    @DisplayName("Company 가 존재하면 응답의 data 를 반환")
    void getCompany_success() {
        // given
        CompanyInfo company = new CompanyInfo(COMPANY_ID, null, HUB_ID, "커넥트상사", "서울특별시 강남구 테헤란로 1");
        given(companyFeignApi.getCompany(COMPANY_ID)).willReturn(ApiResponse.ok("업체가 조회되었습니다.", company));

        // when
        CompanyInfo result = companyFeignClient.getCompany(COMPANY_ID);

        // then
        assertThat(result).isEqualTo(company);
    }

    @Test
    @DisplayName("Company Service 가 404 를 반환하면 COMPANY_NOT_FOUND")
    void getCompany_notFound() {
        // given
        given(companyFeignApi.getCompany(COMPANY_ID)).willThrow(new FeignException.NotFound("Not Found", REQUEST, null, null));

        // when & then
        assertThatThrownBy(() -> companyFeignClient.getCompany(COMPANY_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("Company Service 가 500 을 반환하면 COMPANY_NOT_FOUND 로 바꾸지 않고 그대로 던짐")
    void getCompany_serverError() {
        // given
        given(companyFeignApi.getCompany(COMPANY_ID))
                .willThrow(new FeignException.InternalServerError("Internal Server Error", REQUEST, null, null));

        // when & then
        assertThatThrownBy(() -> companyFeignClient.getCompany(COMPANY_ID))
                .isInstanceOf(FeignException.InternalServerError.class);
    }

    @Test
    @DisplayName("Company Service 연결에 실패하면 COMPANY_NOT_FOUND 로 바꾸지 않고 그대로 던짐")
    void getCompany_connectionFailed() {
        // given
        given(companyFeignApi.getCompany(COMPANY_ID))
                .willThrow(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, REQUEST));

        // when & then
        assertThatThrownBy(() -> companyFeignClient.getCompany(COMPANY_ID))
                .isInstanceOf(RetryableException.class);
    }
}
