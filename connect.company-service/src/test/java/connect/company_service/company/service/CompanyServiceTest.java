package connect.company_service.company.service;

import connect.company_service.client.hub.HubClient;
import connect.company_service.client.hub.HubValidator;
import connect.company_service.client.hub.dto.StockDeleteRequest;
import connect.company_service.company.dto.request.AssignManagerRequest;
import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.InternalCreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.dto.PagedResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import connect.company_service.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    private static final String ROLE_MASTER = "MASTER";
    private static final String ROLE_USER = "USER";

    @Mock
    private CompanyRepository companyRepository;

    // 실제 Hub Service를 호출하지 않도록 Hub 연동 객체는 Mock 처리
    @Mock
    private HubValidator hubValidator;

    @Mock
    private HubClient hubClient;

    // 업체 삭제 시 상품 일괄 soft delete를 위임하는 대상
    @Mock
    private ProductService productService;

    @InjectMocks
    private CompanyService companyService;

    private final UUID userId = UUID.randomUUID();
    private final UUID hubId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    private Company createCompany(UUID id, String name) {
        Company company = Company.create(userId, hubId, name, "서울시 강남구");
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    // MASTER가 생성해 관리자가 아직 지정되지 않은 업체
    private Company createUnassignedCompany(UUID id, String name) {
        Company company = Company.create(null, hubId, name, "서울시 강남구");
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    private void assertBaseException(Throwable thrown, ErrorCode errorCode) {
        assertThat(thrown).isInstanceOf(BaseException.class);
        assertThat(((BaseException) thrown).getErrorCode()).isEqualTo(errorCode);
    }

    @Nested
    @DisplayName("업체 생성")
    class CreateCompany {

        private final CreateCompanyRequest request = new CreateCompanyRequest(hubId, "스파르타 업체", "서울시 강남구");

        @Test
        @DisplayName("MASTER가 생성하면 관리자가 지정되지 않은(userId null) 업체가 된다")
        void createByMaster() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

            CompanyResponse response = companyService.createCompany(userId, ROLE_MASTER, request);

            assertThat(response.userId()).isNull();
            assertThat(response.hubId()).isEqualTo(hubId);
            assertThat(response.name()).isEqualTo(request.name());
            assertThat(response.address()).isEqualTo(request.address());
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("USER가 생성하면 본인이 업체 관리자(userId)가 된다")
        void createByUser() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

            CompanyResponse response = companyService.createCompany(userId, ROLE_USER, request);

            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.name()).isEqualTo(request.name());
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("이미 존재하는 업체명이면 업체를 생성할 수 없다")
        void createWithDuplicateName() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(true);

            Throwable thrown = catchThrowable(() -> companyService.createCompany(
                    userId, ROLE_MASTER, request));

            assertBaseException(thrown, ErrorCode.COMPANY_ALREADY_EXISTS);
            verify(companyRepository, never()).save(any(Company.class));
        }

        @Test
        @DisplayName("요청의 hubId가 존재하는 Hub인지 확인한 뒤 업체를 저장한다")
        void createValidatesHub() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

            companyService.createCompany(userId, ROLE_MASTER, request);

            verify(hubValidator).validateHubExists(hubId);
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("존재하지 않는 Hub면 HUB_NOT_FOUND 예외가 발생하고 업체를 저장하지 않는다")
        void createWithHubNotFound() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND)).given(hubValidator).validateHubExists(hubId);

            Throwable thrown = catchThrowable(() -> companyService.createCompany(
                    userId, ROLE_MASTER, request));

            assertBaseException(thrown, ErrorCode.HUB_NOT_FOUND);
            verify(companyRepository, never()).save(any(Company.class));
        }
    }

    @Nested
    @DisplayName("업체 단건 조회")
    class GetCompany {

        @Test
        @DisplayName("존재하는 업체를 조회한다")
        void getCompany() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            CompanyResponse response = companyService.getCompany(companyId);

            assertThat(response.id()).isEqualTo(companyId);
            assertThat(response.name()).isEqualTo("스파르타 업체");
        }

        @Test
        @DisplayName("업체가 없으면 COMPANY_NOT_FOUND 예외가 발생한다")
        void getCompanyNotFound() {
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> companyService.getCompany(companyId))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("업체 목록 조회 및 검색")
    class GetCompanies {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("검색어가 없으면 전체 목록을 생성일 역순으로 조회한다")
        void getCompaniesWithoutKeyword(String keyword) {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByDeletedAtIsNull(any(Pageable.class)))
                    .willAnswer(invocation -> new PageImpl<>(List.of(company), invocation.getArgument(0), 1));

            PagedResponse<CompanyResponse> response = companyService.getCompanies(keyword, 1, 20);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(companyRepository).findByDeletedAtIsNull(captor.capture());
            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(1);
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);

            assertThat(response.contents()).extracting(CompanyResponse::id).containsExactly(companyId);
            verify(companyRepository, never())
                    .findByNameContainingAndDeletedAtIsNullOrAddressContainingAndDeletedAtIsNull(
                            anyString(), anyString(), any(Pageable.class));
        }

        @Test
        @DisplayName("검색어가 있으면 앞뒤 공백을 제거해 업체명 또는 주소로 검색한다")
        void getCompaniesWithKeyword() {
            Page<Company> page = new PageImpl<>(List.of());
            given(companyRepository.findByNameContainingAndDeletedAtIsNullOrAddressContainingAndDeletedAtIsNull(
                    eq("스파르타"), eq("스파르타"), any(Pageable.class))).willReturn(page);

            PagedResponse<CompanyResponse> response = companyService.getCompanies("  스파르타  ", 0, 10);

            assertThat(response.contents()).isEmpty();
            verify(companyRepository, never()).findByDeletedAtIsNull(any(Pageable.class));
        }

        @ParameterizedTest
        @CsvSource({"-1, 10", "0, 0", "0, 101"})
        @DisplayName("페이지 번호나 크기가 범위를 벗어나면 INVALID_INPUT 예외가 발생한다")
        void getCompaniesWithInvalidPage(int page, int size) {
            assertThatThrownBy(() -> companyService.getCompanies(null, page, size))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
            verifyNoInteractions(companyRepository);
        }
    }

    @Nested
    @DisplayName("업체 수정")
    class UpdateCompany {

        private final UUID newHubId = UUID.randomUUID();
        private final UpdateCompanyRequest request = new UpdateCompanyRequest(newHubId, "새 업체명", "부산시 해운대구");

        @Test
        @DisplayName("MASTER는 업체를 수정할 수 있다")
        void updateByMaster() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);

            CompanyResponse response = companyService.updateCompany(
                    companyId, UUID.randomUUID(), ROLE_MASTER, request);

            assertThat(company.getHubId()).isEqualTo(newHubId);
            assertThat(company.getName()).isEqualTo("새 업체명");
            assertThat(company.getAddress()).isEqualTo("부산시 해운대구");
            assertThat(response.name()).isEqualTo("새 업체명");
        }

        @Test
        @DisplayName("업체를 생성한 사용자는 업체를 수정할 수 있다")
        void updateByOwner() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);

            companyService.updateCompany(companyId, userId, ROLE_USER, request);

            assertThat(company.getName()).isEqualTo("새 업체명");
        }

        @Test
        @DisplayName("업체를 생성한 사용자가 아니면 업체를 수정할 수 없다")
        void updateByNonOwner() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, UUID.randomUUID(), ROLE_USER, request));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            assertThat(company.getName()).isEqualTo("스파르타 업체");
        }

        @Test
        @DisplayName("관리자가 지정되지 않은 업체는 USER가 수정할 수 없다")
        void updateUnassignedByUser() {
            Company company = createUnassignedCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, userId, ROLE_USER, request));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            assertThat(company.getName()).isEqualTo("스파르타 업체");
        }

        @Test
        @DisplayName("업체가 없으면 권한과 관계없이 COMPANY_NOT_FOUND 예외가 발생한다")
        void updateNotFound() {
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, UUID.randomUUID(), ROLE_USER, request));

            assertBaseException(thrown, ErrorCode.COMPANY_NOT_FOUND);
        }

        @Test
        @DisplayName("변경할 업체명이 이미 존재하면 COMPANY_ALREADY_EXISTS 예외가 발생한다")
        void updateWithDuplicateName() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(true);

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, UUID.randomUUID(), ROLE_MASTER, request));

            assertBaseException(thrown, ErrorCode.COMPANY_ALREADY_EXISTS);
            assertThat(company.getName()).isEqualTo("스파르타 업체");
        }

        @Test
        @DisplayName("업체명이 그대로면 중복 검사를 하지 않는다")
        void updateWithSameName() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            UpdateCompanyRequest sameNameRequest = new UpdateCompanyRequest(newHubId, "스파르타 업체", "부산시 해운대구");

            companyService.updateCompany(companyId, UUID.randomUUID(), ROLE_MASTER, sameNameRequest);

            verify(companyRepository, never()).existsByNameAndDeletedAtIsNull(anyString());
            assertThat(company.getAddress()).isEqualTo("부산시 해운대구");
        }

        @Test
        @DisplayName("변경할 hubId가 존재하는 Hub인지 확인한 뒤 업체를 수정한다")
        void updateValidatesHub() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);

            companyService.updateCompany(companyId, userId, ROLE_USER, request);

            verify(hubValidator).validateHubExists(newHubId);
            assertThat(company.getHubId()).isEqualTo(newHubId);
        }

        @Test
        @DisplayName("존재하지 않는 Hub면 HUB_NOT_FOUND 예외가 발생하고 업체를 수정하지 않는다")
        void updateWithHubNotFound() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND)).given(hubValidator).validateHubExists(newHubId);

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, userId, ROLE_USER, request));

            assertBaseException(thrown, ErrorCode.HUB_NOT_FOUND);
            // 수정 전에 중단되므로 기존 값이 유지된다
            assertThat(company.getHubId()).isEqualTo(hubId);
            assertThat(company.getName()).isEqualTo("스파르타 업체");
        }
    }

    @Nested
    @DisplayName("업체 삭제")
    class DeleteCompany {

        @Test
        @DisplayName("MASTER는 업체를 삭제할 수 있다")
        void deleteByMaster() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            companyService.deleteCompany(companyId, userId, ROLE_MASTER);

            assertThat(company.getDeletedAt()).isNotNull();
            assertThat(company.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("업체를 생성한 사용자는 업체를 삭제할 수 있다")
        void deleteByOwner() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            companyService.deleteCompany(companyId, userId, ROLE_USER);

            assertThat(company.getDeletedAt()).isNotNull();
            assertThat(company.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("업체를 생성한 사용자가 아니면 업체를 삭제할 수 없다")
        void deleteByNonOwner() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            Throwable thrown = catchThrowable(() -> companyService.deleteCompany(
                    companyId, UUID.randomUUID(), ROLE_USER));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            assertThat(company.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("관리자가 지정되지 않은 업체는 USER가 삭제할 수 없다")
        void deleteUnassignedByUser() {
            Company company = createUnassignedCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            Throwable thrown = catchThrowable(() -> companyService.deleteCompany(companyId, userId, ROLE_USER));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            assertThat(company.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("업체가 없으면 COMPANY_NOT_FOUND 예외가 발생한다")
        void deleteNotFound() {
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> companyService.deleteCompany(
                    companyId, userId, ROLE_MASTER));

            assertBaseException(thrown, ErrorCode.COMPANY_NOT_FOUND);
        }

        @Test
        @DisplayName("상품이 있으면 상품을 일괄 soft delete하고 삭제된 상품 ID로 Hub 재고 삭제를 요청한다")
        void deleteWithProducts() {
            Company company = createCompany(companyId, "스파르타 업체");
            List<UUID> productIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(productService.deleteProductsByCompany(companyId, userId)).willReturn(productIds);

            companyService.deleteCompany(companyId, userId, ROLE_USER);

            verify(productService).deleteProductsByCompany(companyId, userId);
            verify(hubClient).deleteStocks(eq(userId), eq(new StockDeleteRequest(productIds)));
            assertThat(company.getDeletedAt()).isNotNull();
            assertThat(company.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("상품이 없으면 Hub 재고 삭제를 호출하지 않고 업체만 삭제한다")
        void deleteWithoutProducts() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(productService.deleteProductsByCompany(companyId, userId)).willReturn(List.of());

            companyService.deleteCompany(companyId, userId, ROLE_USER);

            verify(productService).deleteProductsByCompany(companyId, userId);
            verify(hubClient, never()).deleteStocks(any(), any());
            assertThat(company.getDeletedAt()).isNotNull();
            assertThat(company.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Hub 재고 삭제가 실패하면 예외를 그대로 전파한다")
        void deleteWithHubFailure() {
            Company company = createCompany(companyId, "스파르타 업체");
            List<UUID> productIds = List.of(UUID.randomUUID());
            RuntimeException hubError = new RuntimeException("Hub Service 오류");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(productService.deleteProductsByCompany(companyId, userId)).willReturn(productIds);
            given(hubClient.deleteStocks(eq(userId), eq(new StockDeleteRequest(productIds)))).willThrow(hubError);

            // 예외를 삼키지 않고 전파해야 @Transactional이 상품·업체 soft delete를 롤백한다
            Throwable thrown = catchThrowable(() -> companyService.deleteCompany(
                    companyId, userId, ROLE_USER));

            assertThat(thrown).isSameAs(hubError);
        }
    }

    @Nested
    @DisplayName("업체 관리자 지정")
    class AssignManager {

        private final UUID managerId = UUID.randomUUID();
        private final AssignManagerRequest request = new AssignManagerRequest(managerId);

        @Test
        @DisplayName("관리자가 지정되지 않은 업체면 요청한 사용자가 관리자로 지정된다")
        void assign() {
            Company company = createUnassignedCompany(companyId, "스파르타 업체");
            given(companyRepository.findWithLockByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            CompanyResponse response = companyService.assignManager(companyId, request);

            assertThat(company.getUserId()).isEqualTo(managerId);
            assertThat(response.userId()).isEqualTo(managerId);
        }

        @Test
        @DisplayName("이미 관리자가 있으면 예외 없이 기존 관리자를 유지한다")
        void assignAlreadyAssigned() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findWithLockByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            CompanyResponse response = companyService.assignManager(companyId, request);

            assertThat(company.getUserId()).isEqualTo(userId);
            assertThat(response.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("업체가 없으면 COMPANY_NOT_FOUND 예외가 발생한다")
        void assignNotFound() {
            given(companyRepository.findWithLockByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> companyService.assignManager(companyId, request));

            assertBaseException(thrown, ErrorCode.COMPANY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("회원가입 시 업체 생성")
    class CreateCompanyForSignUp {

        private final InternalCreateCompanyRequest request =
                new InternalCreateCompanyRequest(userId, hubId, "스파르타 업체", "서울시 강남구");

        @Test
        @DisplayName("가입하는 사용자가 관리자(userId)인 업체를 생성한다")
        void createForSignUp() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

            CompanyResponse response = companyService.createCompanyForSignUp(request);

            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.hubId()).isEqualTo(hubId);
            assertThat(response.name()).isEqualTo(request.name());
            verify(hubValidator).validateHubExists(hubId);
        }

        @Test
        @DisplayName("이미 존재하는 업체명이면 COMPANY_ALREADY_EXISTS 예외가 발생하고 저장하지 않는다")
        void createForSignUpWithDuplicateName() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(true);

            Throwable thrown = catchThrowable(() -> companyService.createCompanyForSignUp(request));

            assertBaseException(thrown, ErrorCode.COMPANY_ALREADY_EXISTS);
            verify(companyRepository, never()).save(any(Company.class));
        }

        @Test
        @DisplayName("존재하지 않는 Hub면 HUB_NOT_FOUND 예외가 발생하고 저장하지 않는다")
        void createForSignUpWithHubNotFound() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND)).given(hubValidator).validateHubExists(hubId);

            Throwable thrown = catchThrowable(() -> companyService.createCompanyForSignUp(request));

            assertBaseException(thrown, ErrorCode.HUB_NOT_FOUND);
            verify(companyRepository, never()).save(any(Company.class));
        }
    }
}
