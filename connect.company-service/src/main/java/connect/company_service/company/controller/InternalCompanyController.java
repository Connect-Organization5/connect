package connect.company_service.company.controller;

import connect.company_service.company.controller.docs.InternalCompanyControllerDocs;
import connect.company_service.company.dto.request.AssignManagerRequest;
import connect.company_service.company.dto.request.InternalCreateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.company.service.CompanyService;
import connect.company_service.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 다른 서비스가 Feign으로 호출하는 내부 API (Gateway 라우트에 노출하지 않는다)
// 회원가입 중에 호출되어 사용자 헤더(X-User-Id, X-User-Role)가 없으므로 권한 검사를 하지 않는다
@RestController
@RequestMapping("/internal/companies")
@RequiredArgsConstructor
public class InternalCompanyController implements InternalCompanyControllerDocs {

    private final CompanyService companyService;

    // 회원가입 시 새 업체 생성 (호출: user-service / 허용 권한: 내부 호출만)
    // 가입하는 사용자가 업체 관리자가 되고, 응답의 data.id를 user의 affiliationId로 저장한다
    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody InternalCreateCompanyRequest request) {

        CompanyResponse response = companyService.createCompanyForSignUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("업체가 생성되었습니다.", response));
    }

    // 회원가입 시 기존 업체를 고른 경우 (호출: user-service / 허용 권한: 내부 호출만)
    // 관리자가 없는 업체(MASTER가 생성)면 가입자를 관리자로 지정하고, 이미 있으면 변경 없이 200
    @PatchMapping("/{companyId}/manager")
    public ResponseEntity<ApiResponse<CompanyResponse>> assignManager(
            @PathVariable UUID companyId,
            @Valid @RequestBody AssignManagerRequest request) {

        CompanyResponse response = companyService.assignManager(companyId, request);
        return ResponseEntity.ok(ApiResponse.ok("업체 연결이 처리되었습니다.", response));
    }
}
