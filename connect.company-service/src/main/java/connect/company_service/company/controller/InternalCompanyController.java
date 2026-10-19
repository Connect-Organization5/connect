package connect.company_service.company.controller;

import connect.company_service.company.dto.request.AssignManagerRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.service.CompanyService;
import connect.company_service.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 다른 서비스가 Feign으로 호출하는 내부 API (Gateway 라우트에 노출하지 않는다)
@RestController
@RequestMapping("/internal/companies")
@RequiredArgsConstructor
public class InternalCompanyController {

    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final CompanyService companyService;

    // 업체 관리자 지정 (호출: user-service / 허용 권한: MASTER)
    // 관리자가 없는 업체에만 지정되고, 이미 관리자가 있으면 409 COMPANY_MANAGER_ALREADY_ASSIGNED
    @PatchMapping("/{companyId}/manager")
    public ResponseEntity<ApiResponse<CompanyResponse>> assignManager(
            @PathVariable UUID companyId,
            @RequestHeader(USER_ROLE_HEADER) String userRole,
            @Valid @RequestBody AssignManagerRequest request) {

        CompanyResponse response = companyService.assignManager(companyId, userRole, request);
        return ResponseEntity.ok(ApiResponse.ok("업체 관리자가 지정되었습니다.", response));
    }
}
