package connect.company_service.company.service;

import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.dto.PagedResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    private static final String ROLE_MASTER = "MASTER";
    private static final String ROLE_USER = "USER";
    private static final String AFFILIATION_COMPANY = "COMPANY";
    private static final String AFFILIATION_HUB = "HUB";

    @Mock
    private CompanyRepository companyRepository;

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

    private void assertBaseException(Throwable thrown, ErrorCode errorCode) {
        assertThat(thrown).isInstanceOf(BaseException.class);
        assertThat(((BaseException) thrown).getErrorCode()).isEqualTo(errorCode);
    }

    @Nested
    @DisplayName("업체 생성")
    class CreateCompany {

        private final CreateCompanyRequest request = new CreateCompanyRequest(hubId, "스파르타 업체", "서울시 강남구");

        @Test
        @DisplayName("MASTER는 소속과 관계없이 업체를 생성할 수 있다")
        void createByMaster() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

            CompanyResponse response = companyService.createCompany(
                    userId, ROLE_MASTER, AFFILIATION_HUB, UUID.randomUUID(), request);

            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.hubId()).isEqualTo(hubId);
            assertThat(response.name()).isEqualTo(request.name());
            assertThat(response.address()).isEqualTo(request.address());
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("소속 업체가 없는 업체 소속 사용자는 업체를 생성할 수 있다")
        void createByCompanyUserWithoutAffiliation() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);
            given(companyRepository.save(any(Company.class))).willAnswer(invocation -> invocation.getArgument(0));

            CompanyResponse response = companyService.createCompany(
                    userId, ROLE_USER, AFFILIATION_COMPANY, null, request);

            assertThat(response.name()).isEqualTo(request.name());
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("이미 소속 업체가 있는 사용자는 업체를 생성할 수 없다")
        void createByCompanyUserWithAffiliation() {
            Throwable thrown = catchThrowable(() -> companyService.createCompany(
                    userId, ROLE_USER, AFFILIATION_COMPANY, UUID.randomUUID(), request));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            verifyNoInteractions(companyRepository);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {AFFILIATION_HUB})
        @DisplayName("업체 소속이 아닌 사용자는 업체를 생성할 수 없다")
        void createByNonCompanyUser(String affiliationType) {
            Throwable thrown = catchThrowable(() -> companyService.createCompany(
                    userId, ROLE_USER, affiliationType, null, request));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            verifyNoInteractions(companyRepository);
        }

        @Test
        @DisplayName("이미 존재하는 업체명이면 업체를 생성할 수 없다")
        void createWithDuplicateName() {
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(true);

            Throwable thrown = catchThrowable(() -> companyService.createCompany(
                    userId, ROLE_MASTER, null, null, request));

            assertBaseException(thrown, ErrorCode.COMPANY_ALREADY_EXISTS);
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
                    companyId, ROLE_MASTER, null, null, request);

            assertThat(company.getHubId()).isEqualTo(newHubId);
            assertThat(company.getName()).isEqualTo("새 업체명");
            assertThat(company.getAddress()).isEqualTo("부산시 해운대구");
            assertThat(response.name()).isEqualTo("새 업체명");
        }

        @Test
        @DisplayName("해당 업체 소속 사용자는 업체를 수정할 수 있다")
        void updateByCompanyMember() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(false);

            companyService.updateCompany(companyId, ROLE_USER, AFFILIATION_COMPANY, companyId, request);

            assertThat(company.getName()).isEqualTo("새 업체명");
        }

        @ParameterizedTest
        @ValueSource(strings = {AFFILIATION_COMPANY, AFFILIATION_HUB})
        @DisplayName("해당 업체 소속이 아니면 업체를 수정할 수 없다")
        void updateByNonMember(String affiliationType) {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, ROLE_USER, affiliationType, UUID.randomUUID(), request));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            assertThat(company.getName()).isEqualTo("스파르타 업체");
        }

        @Test
        @DisplayName("업체가 없으면 권한과 관계없이 COMPANY_NOT_FOUND 예외가 발생한다")
        void updateNotFound() {
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, ROLE_USER, AFFILIATION_HUB, null, request));

            assertBaseException(thrown, ErrorCode.COMPANY_NOT_FOUND);
        }

        @Test
        @DisplayName("변경할 업체명이 이미 존재하면 COMPANY_ALREADY_EXISTS 예외가 발생한다")
        void updateWithDuplicateName() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            given(companyRepository.existsByNameAndDeletedAtIsNull(request.name())).willReturn(true);

            Throwable thrown = catchThrowable(() -> companyService.updateCompany(
                    companyId, ROLE_MASTER, null, null, request));

            assertBaseException(thrown, ErrorCode.COMPANY_ALREADY_EXISTS);
            assertThat(company.getName()).isEqualTo("스파르타 업체");
        }

        @Test
        @DisplayName("업체명이 그대로면 중복 검사를 하지 않는다")
        void updateWithSameName() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));
            UpdateCompanyRequest sameNameRequest = new UpdateCompanyRequest(newHubId, "스파르타 업체", "부산시 해운대구");

            companyService.updateCompany(companyId, ROLE_MASTER, null, null, sameNameRequest);

            verify(companyRepository, never()).existsByNameAndDeletedAtIsNull(anyString());
            assertThat(company.getAddress()).isEqualTo("부산시 해운대구");
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

            companyService.deleteCompany(companyId, userId, ROLE_MASTER, null, null);

            assertThat(company.getDeletedAt()).isNotNull();
            assertThat(company.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("해당 업체 소속 사용자는 업체를 삭제할 수 있다")
        void deleteByCompanyMember() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            companyService.deleteCompany(companyId, userId, ROLE_USER, AFFILIATION_COMPANY, companyId);

            assertThat(company.getDeletedAt()).isNotNull();
            assertThat(company.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("다른 업체 소속 사용자는 업체를 삭제할 수 없다")
        void deleteByOtherCompanyMember() {
            Company company = createCompany(companyId, "스파르타 업체");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.of(company));

            Throwable thrown = catchThrowable(() -> companyService.deleteCompany(
                    companyId, userId, ROLE_USER, AFFILIATION_COMPANY, UUID.randomUUID()));

            assertBaseException(thrown, ErrorCode.COMPANY_ACCESS_DENIED);
            assertThat(company.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("업체가 없으면 COMPANY_NOT_FOUND 예외가 발생한다")
        void deleteNotFound() {
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId)).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> companyService.deleteCompany(
                    companyId, userId, ROLE_MASTER, null, null));

            assertBaseException(thrown, ErrorCode.COMPANY_NOT_FOUND);
        }
    }
}
