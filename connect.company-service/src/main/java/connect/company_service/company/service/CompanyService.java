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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
                                         String companyName, CreateCompanyRequest request) {
        String name;
        if (ROLE_MASTER.equals(userRole)) {
            if (request.name() == null || request.name().isBlank()) {
                throw new BaseException(ErrorCode.VALIDATION_FAILED);
            }
            name = request.name();
        } else {
            if (!AFFILIATION_COMPANY.equals(affiliationType) || companyName == null || companyName.isBlank()) {
                throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
            }

            name = decodeCompanyName(companyName);
        }

        if (companyRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        // TODO: hub-service 연동 후 hubId 존재 여부 검증 추가

        Company company = companyRepository.save(
                Company.create(userId, request.hubId(), name, request.address()));
        return CompanyResponse.from(company);
    }

    // 업체 단건 조회
    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID companyId) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));
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
                                         String companyName, UpdateCompanyRequest request) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));

        validateCompanyAccess(company, userRole, affiliationType, companyName);

        boolean nameChanged = !company.getName().equals(request.name());
        if (nameChanged && companyRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new BaseException(ErrorCode.COMPANY_ALREADY_EXISTS);
        }

        // TODO: hub-service 연동 후 hubId 존재 여부 검증 추가

        company.update(request.hubId(), request.name(), request.address());

        // TODO: user-service 연동 방식 확정 후 업체명 변경 시 user-service에 알림

        return CompanyResponse.from(company);
    }

    // 업체 삭제
    @Transactional
    public void deleteCompany(UUID companyId, UUID userId, String userRole, String affiliationType,
                              String companyName) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new BaseException(ErrorCode.COMPANY_NOT_FOUND));

        validateCompanyAccess(company, userRole, affiliationType, companyName);

        company.delete(userId);

        // TODO: product-service 연동 방식 확정 후 업체 삭제 시 상품 처리
    }

    // MASTER이거나, 해당 업체명으로 가입한 업체 소속 사용자만 허용
    private void validateCompanyAccess(Company company, String userRole, String affiliationType, String companyName) {
        if (ROLE_MASTER.equals(userRole)) {
            return;
        }
        if (!AFFILIATION_COMPANY.equals(affiliationType) || companyName == null || companyName.isBlank()
                || !company.getName().equals(decodeCompanyName(companyName))) {
            throw new BaseException(ErrorCode.COMPANY_ACCESS_DENIED);
        }
    }

    // TODO: Gateway와 인코딩 방식 확정 후 변경
    private String decodeCompanyName(String companyName) {
        return URLDecoder.decode(companyName, StandardCharsets.UTF_8);
    }
}
