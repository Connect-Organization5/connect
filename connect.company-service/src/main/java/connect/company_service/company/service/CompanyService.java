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
    public CompanyResponse createCompany(String userRole, CreateCompanyRequest request) {
        // 업체 생성은 MASTER만 가능. 관리자는 첫 가입자가 내부 API(assignManager)로 지정된다
        if (!ROLE_MASTER.equals(userRole)) {
            throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
        }

        return saveCompany(null, request.hubId(), request.name(), request.address());
    }

    // 회원가입 시 업체 생성 (user-service 내부 호출). 가입하는 사용자가 관리자가 된다
    @Transactional
    public CompanyResponse createCompanyForSignUp(InternalCreateCompanyRequest request) {
        return saveCompany(request.userId(), request.hubId(), request.name(), request.address());
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

    // 회원가입 시 기존 업체를 고른 경우 (user-service 내부 호출)
    // 관리자가 없는 업체(MASTER가 생성)면 처음 가입한 직원을 관리자로 지정하고, 이미 있으면 변경하지 않는다
    @Transactional
    public CompanyResponse assignManager(UUID companyId, AssignManagerRequest request) {
        // 행 잠금으로 조회해 동시에 가입해도 먼저 들어온 직원만 관리자가 된다
        Company company = companyRepository.findWithLockByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));

        if (company.getUserId() == null) {
            company.assignManager(request.userId());
        }
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

    // 업체명 중복과 Hub 존재를 확인한 뒤 저장
    private CompanyResponse saveCompany(UUID managerId, UUID hubId, String name, String address) {
        if (companyRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        hubValidator.validateHubExists(hubId);

        Company company = companyRepository.save(Company.create(managerId, hubId, name, address));
        return CompanyResponse.from(company);
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
