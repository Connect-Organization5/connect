package connect.company_service.company.service;

import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.dto.PagedResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final String ROLE_MASTER = "MASTER";
    private static final String AFFILIATION_COMPANY = "COMPANY";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final CompanyRepository companyRepository;

    // 업체 생성
    @Transactional
    public CompanyResponse createCompany(UUID userId, String userRole, String affiliationType,
                                         UUID affiliationId, CreateCompanyRequest request) {
        validateCreateAccess(userRole, affiliationType, affiliationId);

        if (companyRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        // TODO: hub-service 연동 후 hubId 존재 여부 검증 추가

        Company company = companyRepository.save(
                Company.create(userId, request.hubId(), request.name(), request.address()));
        return CompanyResponse.from(company);
    }

    // 업체 단건 조회
    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID companyId) {
        Company company = findActiveCompany(companyId);
        return CompanyResponse.from(company);
    }

    // 업체 목록 조회 및 검색
    @Transactional(readOnly = true)
    public PagedResponse<CompanyResponse> getCompanies(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        PageRequest pageable = PageRequest.of(page, size, DEFAULT_SORT);
        Page<Company> companies = (keyword == null || keyword.isBlank())
                ? companyRepository.findByDeletedAtIsNull(pageable)
                : companyRepository.findByNameContainingAndDeletedAtIsNullOrAddressContainingAndDeletedAtIsNull(
                        keyword.trim(), keyword.trim(), pageable);

        return PagedResponse.from(companies.map(CompanyResponse::from));
    }

    // 업체 수정
    @Transactional
    public CompanyResponse updateCompany(UUID companyId, String userRole, String affiliationType,
                                         UUID affiliationId, UpdateCompanyRequest request) {
        Company company = findActiveCompany(companyId);

        validateCompanyAccess(company, userRole, affiliationType, affiliationId);

        boolean nameChanged = !company.getName().equals(request.name());
        if (nameChanged && companyRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        // TODO: hub-service 연동 후 hubId 존재 여부 검증 추가

        company.update(request.hubId(), request.name(), request.address());

        return CompanyResponse.from(company);
    }

    // 업체 삭제
    @Transactional
    public void deleteCompany(UUID companyId, UUID userId, String userRole, String affiliationType,
                              UUID affiliationId) {
        Company company = findActiveCompany(companyId);

        validateCompanyAccess(company, userRole, affiliationType, affiliationId);

        company.delete(userId);

        // TODO: product-service 연동 방식 확정 후 업체 삭제 시 상품 처리
    }

    // 삭제되지 않은 업체 조회
    private Company findActiveCompany(UUID companyId) {
        return companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));
    }

    // 업체 생성 권한 검증 (MASTER이거나 아직 소속 업체가 없는 업체 관리자)
    private void validateCreateAccess(String userRole, String affiliationType, UUID affiliationId) {
        if (ROLE_MASTER.equals(userRole)) {
            return;
        }
        if (!AFFILIATION_COMPANY.equals(affiliationType) || affiliationId != null) {
            throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
        }
    }

    // 업체 수정 및 삭제 권한 검증 (MASTER, 해당 업체에 소속된 사용자)
    private void validateCompanyAccess(Company company, String userRole, String affiliationType, UUID affiliationId) {
        if (ROLE_MASTER.equals(userRole)) {
            return;
        }
        if (!AFFILIATION_COMPANY.equals(affiliationType) || !company.getId().equals(affiliationId)) {
            throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
        }
    }
}
