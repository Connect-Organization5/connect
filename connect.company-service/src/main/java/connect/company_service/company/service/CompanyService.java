package connect.company_service.company.service;

import connect.company_service.client.hub.HubClient;
import connect.company_service.client.hub.HubValidator;
import connect.company_service.client.hub.dto.StockDeleteRequest;
import connect.company_service.client.user.UserValidator;
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
    private final UserValidator userValidator;

    // 업체 생성
    @Transactional
    public CompanyResponse createCompany(String userRole, CreateCompanyRequest request) {
        // 업체 생성은 MASTER만 가능. 관리자는 직원 가입 후 MASTER가 assignManager로 지정한다
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

        // 소속 허브는 생성 후 변경할 수 없다. Hub 별 재고와 주문의 출발 허브가 어긋나지 않도록 다른 hubId 요청은 거부한다
        if (!company.getHubId().equals(request.hubId())) {
            throw new BaseException(ErrorCode.COMPANY_HUB_CHANGE_NOT_ALLOWED);
        }

        boolean nameChanged = !company.getName().equals(request.name());
        if (nameChanged && companyRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        company.update(request.name(), request.address());

        return CompanyResponse.from(company);
    }

    // 업체 관리자 지정 (MASTER만 가능). 이미 관리자가 있으면 요청한 사용자로 변경한다
    @Transactional
    public CompanyResponse assignManager(UUID companyId, UUID userId, String userRole, AssignManagerRequest request) {
        if (!ROLE_MASTER.equals(userRole)) {
            throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
        }

        Company company = findActiveCompany(companyId);

        // 대상이 없는 사용자면 USER_NOT_FOUND, 이 업체 소속(COMPANY + affiliationId)이 아니면 COMPANY_MANAGER_NOT_MEMBER
        userValidator.validateCompanyMember(userId, userRole, request.userId(), companyId);

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
