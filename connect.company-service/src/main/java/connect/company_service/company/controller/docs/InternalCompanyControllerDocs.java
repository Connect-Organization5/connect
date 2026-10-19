package connect.company_service.company.controller.docs;

import connect.company_service.company.dto.request.AssignManagerRequest;
import connect.company_service.company.dto.request.InternalCreateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.global.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

// InternalCompanyController 의 Swagger 문서 (동작은 InternalCompanyController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Internal Company (내부 전용)", description = """
        **서비스 간 내부 호출 전용 API** (호출: user-service, Feign)
        - Gateway 라우트에 없어 외부 클라이언트는 호출할 수 없습니다.
        - 회원가입 도중 호출되므로 사용자 헤더(X-User-Id, X-User-Role)를 받지 않고 권한 검사를 하지 않습니다.
        """)
public interface InternalCompanyControllerDocs {

    @Operation(
            summary = "[내부] 회원가입 시 업체 생성",
            description = """
                    회원가입하는 사용자가 새 업체를 만들 때 user-service 가 호출합니다.
                    요청의 `userId`(가입자)가 업체 관리자가 되며, user-service 는 응답의 `data.id` 를 사용자의 affiliationId 로 저장합니다.

                    **권한**: 내부 호출 전용 (권한 검사 없음)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류 (VALIDATION_FAILED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 Hub (HUB_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 업체명 (COMPANY_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<CompanyResponse>> createCompany(
            InternalCreateCompanyRequest request);

    @Operation(
            summary = "[내부] 회원가입 시 기존 업체 관리자 지정",
            description = """
                    회원가입하는 사용자가 기존 업체를 선택했을 때 user-service 가 호출합니다.
                    관리자가 없는 업체(MASTER 가 생성)면 요청의 `userId` 를 관리자로 지정하고,
                    이미 관리자가 있으면 변경하지 않고 200 을 반환합니다(동시 요청 시 먼저 처리된 사용자만 관리자).

                    **권한**: 내부 호출 전용 (권한 검사 없음)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공 (관리자 지정 또는 변경 없음)"),
            @ApiResponse(responseCode = "400", description = "필수값 누락 (VALIDATION_FAILED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체 (COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<CompanyResponse>> assignManager(
            @Parameter(description = "업체 ID") UUID companyId,
            AssignManagerRequest request);
}
