package connect.company_service.company.service;

import connect.company_service.client.hub.HubClient;
import connect.company_service.client.hub.HubValidator;
import connect.company_service.client.hub.dto.StockDeleteRequest;
import connect.company_service.company.dto.request.AssignManagerRequest;
import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.dto.PagedResponse;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import connect.company_service.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final String ROLE_MASTER = "MASTER";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final CompanyRepository companyRepository;
    private final HubValidator hubValidator;
    private final ProductService productService;
    private final HubClient hubClient;

    // 업체 생성
    @Transactional
    public CompanyResponse createCompany(UUID userId, String userRole, CreateCompanyRequest request) {
        if (companyRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        // 존재하지 않는 Hub면 HUB_NOT_FOUND로 중단되어 저장하지 않는다
        hubValidator.validateHubExists(request.hubId());

        // USER가 만들면 본인이 관리자, MASTER가 만들면 관리자 미지정(null)
        UUID managerId = ROLE_MASTER.equals(userRole) ? null : userId;

        Company company = companyRepository.save(
                Company.create(managerId, request.hubId(), request.name(), request.address()));
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
    public CompanyResponse updateCompany(UUID companyId, UUID userId, String userRole, UpdateCompanyRequest request) {
        Company company = findActiveCompany(companyId);

        validateCompanyAccess(company, userId, userRole);

        boolean nameChanged = !company.getName().equals(request.name());
        if (nameChanged && companyRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        // 존재하지 않는 Hub면 HUB_NOT_FOUND로 중단되어 수정하지 않는다
        hubValidator.validateHubExists(request.hubId());

        company.update(request.hubId(), request.name(), request.address());

        return CompanyResponse.from(company);
    }

    // 업체 관리자 지정 (MASTER만 허용)
    @Transactional
    public CompanyResponse assignManager(UUID companyId, String userRole, AssignManagerRequest request) {
        if (!ROLE_MASTER.equals(userRole)) {
            throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
        }

        // 행 잠금으로 조회해 먼저 들어온 요청만 관리자를 지정한다
        Company company = companyRepository.findWithLockByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));

        if (company.getUserId() != null) {
            throw new BaseException(ErrorCode.COMPANY_MANAGER_ALREADY_ASSIGNED);
        }

        company.assignManager(request.userId());
        return CompanyResponse.from(company);
    }

    // 업체 삭제
    @Transactional
    public void deleteCompany(UUID companyId, UUID userId, String userRole) {
        Company company = findActiveCompany(companyId);

        validateCompanyAccess(company, userId, userRole);

        List<UUID> productIds =
                productService.deleteProductsByCompany(companyId, userId);

        if (!productIds.isEmpty()) {
            hubClient.deleteStocks(
                    userId,
                    new StockDeleteRequest(productIds)
            );
        }

        company.delete(userId);
    }

    // 삭제되지 않은 업체 조회
    private Company findActiveCompany(UUID companyId) {
        return companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));
    }

    // 업체 수정 및 삭제 권한 검증 (MASTER, 업체 관리자)
    // 관리자 미지정(userId == null) 업체는 MASTER만 통과한다
    private void validateCompanyAccess(Company company, UUID userId, String userRole) {
        if (ROLE_MASTER.equals(userRole)) {
            return;
        }
        if (!userId.equals(company.getUserId())) {
            throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
        }
    }
}
