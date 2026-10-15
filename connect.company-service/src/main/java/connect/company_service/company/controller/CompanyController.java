package connect.company_service.company.controller;

import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.service.CompanyService;
import connect.company_service.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    // TODO: Gateway와 소속 헤더 이름 확정되면 변경
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String AFFILIATION_TYPE_HEADER = "X-User-Affiliation-Type";
    private static final String COMPANY_NAME_HEADER = "X-User-Company-Name";

    private final CompanyService companyService;

    // 업체 생성
    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole,
            @RequestHeader(value = AFFILIATION_TYPE_HEADER, required = false) String affiliationType,
            @RequestHeader(value = COMPANY_NAME_HEADER, required = false) String companyName,
            @Valid @RequestBody CreateCompanyRequest request) {

        CompanyResponse response = companyService.createCompany(userId, userRole, affiliationType, companyName, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("업체가 생성되었습니다.", response));
    }

    // 업체 단건 조회
    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID companyId) {
        CompanyResponse response = companyService.getCompany(companyId);
        return ResponseEntity.ok(ApiResponse.ok("업체가 조회되었습니다.", response));
    }
}
