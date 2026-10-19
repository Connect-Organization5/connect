package connect.company_service.company.controller;

import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.service.CompanyService;
import connect.company_service.global.dto.ApiResponse;
import connect.company_service.global.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final CompanyService companyService;

    // 업체 생성 (MASTER만 가능)
    // X-User-Id는 서비스 로직에서 쓰지 않지만 createdBy 기록(JpaAuditingConfig)에 필요해서 필수로 받는다
    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole,
            @Valid @RequestBody CreateCompanyRequest request) {

        CompanyResponse response = companyService.createCompany(userRole, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("업체가 생성되었습니다.", response));
    }

    // 업체 단건 조회
    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable UUID companyId) {
        CompanyResponse response = companyService.getCompany(companyId);
        return ResponseEntity.ok(ApiResponse.ok("업체가 조회되었습니다.", response));
    }

    // 업체 목록 조회 및 검색
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CompanyResponse>>> getCompanies(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PagedResponse<CompanyResponse> response = companyService.getCompanies(keyword, page, size);
        return ResponseEntity.ok(ApiResponse.ok("업체 목록이 조회되었습니다.", response));
    }

    // 업체 수정
    @PutMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @PathVariable UUID companyId,
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole,
            @Valid @RequestBody UpdateCompanyRequest request) {

        CompanyResponse response = companyService.updateCompany(companyId, userId, userRole, request);
        return ResponseEntity.ok(ApiResponse.ok("업체가 수정되었습니다.", response));
    }

    // 업체 삭제
    @DeleteMapping("/{companyId}")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(
            @PathVariable UUID companyId,
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole) {

        companyService.deleteCompany(companyId, userId, userRole);
        return ResponseEntity.ok(ApiResponse.ok("업체가 삭제되었습니다.", null));
    }
}
