package connect.company_service.company.service;

import connect.company_service.client.hub.HubValidator;
import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    // 실제 Hub Service를 호출하지 않도록 Mock 처리
    @Mock
    private HubValidator hubValidator;

    @InjectMocks
    private CompanyService companyService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID hubId = UUID.randomUUID();

    @Test
    void 존재하는_Hub이면_업체를_생성한다() {
        given(companyRepository.existsByNameAndDeletedAtIsNull("사과농장")).willReturn(false);
        given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

        CompanyResponse response = companyService.createCompany(
                ownerId, "MASTER", null, null, new CreateCompanyRequest(hubId, "사과농장", "서울시 송파구"));

        assertThat(response).isNotNull();
        verify(hubValidator).validateHubExists(hubId);
        verify(companyRepository).save(any(Company.class));
    }

    @Test
    void Hub_검증에_실패하면_업체를_저장하지_않는다() {
        given(companyRepository.existsByNameAndDeletedAtIsNull("사과농장")).willReturn(false);
        willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND)).given(hubValidator).validateHubExists(hubId);

        assertThatThrownBy(() -> companyService.createCompany(
                ownerId, "MASTER", null, null, new CreateCompanyRequest(hubId, "사과농장", "서울시 송파구")))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.HUB_NOT_FOUND.getMessage());
        verify(companyRepository, never()).save(any());
    }

    @Test
    void 존재하는_Hub이면_업체를_수정한다() {
        Company company = Company.create(ownerId, UUID.randomUUID(), "사과농장", "서울시 송파구");
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

        companyService.updateCompany(companyId, ownerId, "USER", "COMPANY",
                new UpdateCompanyRequest(hubId, "사과농장", "서울시 강남구"));

        verify(hubValidator).validateHubExists(hubId);
        assertThat(company.getHubId()).isEqualTo(hubId);
        assertThat(company.getAddress()).isEqualTo("서울시 강남구");
    }

    @Test
    void Hub_검증에_실패하면_업체를_수정하지_않는다() {
        UUID originalHubId = UUID.randomUUID();
        Company company = Company.create(ownerId, originalHubId, "사과농장", "서울시 송파구");
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND)).given(hubValidator).validateHubExists(hubId);

        assertThatThrownBy(() -> companyService.updateCompany(companyId, ownerId, "USER", "COMPANY",
                new UpdateCompanyRequest(hubId, "사과농장", "서울시 강남구")))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.HUB_NOT_FOUND.getMessage());
        // 수정 전에 중단되므로 기존 값이 유지된다
        assertThat(company.getHubId()).isEqualTo(originalHubId);
        assertThat(company.getAddress()).isEqualTo("서울시 송파구");
    }
}
