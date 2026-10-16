package connect.company_service.company.service;

import connect.company_service.client.hub.HubClient;
import connect.company_service.client.hub.HubValidator;
import connect.company_service.client.hub.dto.StockDeleteRequest;
import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import connect.company_service.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private ProductService productService;

    @Mock
    private HubClient hubClient;

    @InjectMocks
    private CompanyService companyService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID hubId = UUID.randomUUID();

    // 권한 검증이 company.id와 affiliationId를 비교하므로 id를 지정한 업체를 만든다
    private Company savedCompany(UUID hubId, String address) {
        Company company = Company.create(ownerId, hubId, "사과농장", address);
        ReflectionTestUtils.setField(company, "id", companyId);
        return company;
    }

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
        Company company = savedCompany(UUID.randomUUID(), "서울시 송파구");
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

        companyService.updateCompany(companyId, "USER", "COMPANY", companyId,
                new UpdateCompanyRequest(hubId, "사과농장", "서울시 강남구"));

        verify(hubValidator).validateHubExists(hubId);
        assertThat(company.getHubId()).isEqualTo(hubId);
        assertThat(company.getAddress()).isEqualTo("서울시 강남구");
    }

    @Test
    void Hub_검증에_실패하면_업체를_수정하지_않는다() {
        UUID originalHubId = UUID.randomUUID();
        Company company = savedCompany(originalHubId, "서울시 송파구");
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND)).given(hubValidator).validateHubExists(hubId);

        assertThatThrownBy(() -> companyService.updateCompany(companyId, "USER", "COMPANY", companyId,
                new UpdateCompanyRequest(hubId, "사과농장", "서울시 강남구")))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.HUB_NOT_FOUND.getMessage());
        // 수정 전에 중단되므로 기존 값이 유지된다
        assertThat(company.getHubId()).isEqualTo(originalHubId);
        assertThat(company.getAddress()).isEqualTo("서울시 송파구");
    }

    @Test
    void 상품이_있는_업체를_삭제하면_상품과_Hub_재고도_삭제한다() {
        Company company = savedCompany(hubId, "서울시 송파구");
        List<UUID> productIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productService.deleteProductsByCompany(companyId, ownerId)).willReturn(productIds);

        companyService.deleteCompany(companyId, ownerId, "USER", "COMPANY", companyId);

        verify(productService).deleteProductsByCompany(companyId, ownerId);
        // 일괄 삭제가 반환한 상품 ID 목록을 그대로 Hub 재고 삭제 요청에 담는다
        verify(hubClient).deleteStocks(eq(ownerId), eq(new StockDeleteRequest(productIds)));
        assertThat(company.getDeletedAt()).isNotNull();
        assertThat(company.getDeletedBy()).isEqualTo(ownerId);
    }

    @Test
    void 상품이_없는_업체를_삭제하면_Hub_재고_삭제를_호출하지_않는다() {
        Company company = savedCompany(hubId, "서울시 송파구");
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productService.deleteProductsByCompany(companyId, ownerId)).willReturn(List.of());

        companyService.deleteCompany(companyId, ownerId, "USER", "COMPANY", companyId);

        verify(productService).deleteProductsByCompany(companyId, ownerId);
        verify(hubClient, never()).deleteStocks(any(), any());
        assertThat(company.getDeletedAt()).isNotNull();
        assertThat(company.getDeletedBy()).isEqualTo(ownerId);
    }

    @Test
    void 업체_삭제_중_Hub_재고_삭제가_실패하면_예외를_전파한다() {
        Company company = savedCompany(hubId, "서울시 송파구");
        List<UUID> productIds = List.of(UUID.randomUUID());
        given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
        given(productService.deleteProductsByCompany(companyId, ownerId)).willReturn(productIds);
        given(hubClient.deleteStocks(eq(ownerId), eq(new StockDeleteRequest(productIds))))
                .willThrow(new RuntimeException("Hub Service 오류"));

        // 예외를 삼키지 않고 전파해야 @Transactional이 상품·업체 soft delete를 롤백한다
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> companyService.deleteCompany(companyId, ownerId, "USER", "COMPANY", companyId));
        assertThat(exception).hasMessage("Hub Service 오류");
    }
}
