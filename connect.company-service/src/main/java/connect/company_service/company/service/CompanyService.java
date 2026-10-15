package connect.company_service.company.service;

import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.global.exception.BaseException;
import connect.company_service.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

            // TODO: Gateway와 인코딩 방식 확정 후 변경
            name = URLDecoder.decode(companyName, StandardCharsets.UTF_8);
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
}
