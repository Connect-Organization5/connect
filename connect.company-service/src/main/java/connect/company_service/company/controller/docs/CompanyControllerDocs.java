package connect.company_service.company.controller.docs;

import connect.company_service.company.dto.request.CreateCompanyRequest;
import connect.company_service.company.dto.request.UpdateCompanyRequest;
import connect.company_service.company.dto.response.CompanyResponse;
import connect.company_service.global.dto.ErrorResponse;
import connect.company_service.global.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

// CompanyController 의 Swagger 문서 (동작은 CompanyController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Company", description = "업체 관리 API (Gateway 경유, X-User-Id / X-User-Role 헤더 필요)")
public interface CompanyControllerDocs {

    @Operation(
            summary = "업체 생성",
            description = """
                    업체를 생성합니다. 업체명 중복과 Hub 존재 여부(Hub Service 호출)를 확인한 뒤 저장합니다.

                    **권한**: 로그인한 사용자 누구나
                    - `USER` 가 생성하면 요청자가 업체 관리자(`userId`)가 됩니다.
                    - `MASTER` 가 생성하면 관리자가 지정되지 않습니다(`userId = null`). 이후 회원가입한 직원이 관리자가 됩니다.
                    """,
            security = @SecurityRequirement(name = "gatewayBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED), 필수 헤더 누락(MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 Hub (HUB_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 업체명 (COMPANY_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Hub Service 장애 등 서버 오류 (INTERNAL_SERVER_ERROR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<CompanyResponse>> createCompany(
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true,
                    description = "요청 사용자 ID (Gateway 가 JWT 의 userId 로 설정)") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true,
                    description = "요청 사용자 권한 (MASTER 또는 USER)", example = "USER") String userRole,
            CreateCompanyRequest request);

    @Operation(
            summary = "업체 단건 조회",
            description = """
                    삭제되지 않은 업체를 조회합니다.

                    **권한**: 별도 권한 검사 없음 (사용자 헤더 불필요)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "companyId 가 UUID 형식이 아님 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체 (COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<CompanyResponse>> getCompany(
            @Parameter(description = "업체 ID") UUID companyId);

    @Operation(
            summary = "업체 목록 조회 및 검색",
            description = """
                    삭제되지 않은 업체를 생성일 역순(`createdAt DESC`)으로 조회합니다.
                    `keyword` 가 있으면 업체명 또는 주소에 부분 일치하는 업체만 조회합니다.

                    **권한**: 별도 권한 검사 없음 (사용자 헤더 불필요)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (data.contents 에 목록)"),
            @ApiResponse(responseCode = "400", description = "page < 0, size < 1 또는 size > 100 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<PagedResponse<CompanyResponse>>> getCompanies(
            @Parameter(description = "업체명 또는 주소 검색어 (생략 시 전체)", example = "스파르타") String keyword,
            @Parameter(description = "페이지 번호 (0부터 시작)", schema = @Schema(defaultValue = "0", minimum = "0")) int page,
            @Parameter(description = "페이지 크기 (1~100)", schema = @Schema(defaultValue = "10", minimum = "1", maximum = "100")) int size);

    @Operation(
            summary = "업체 수정",
            description = """
                    업체의 Hub, 업체명, 주소를 수정합니다(PUT 전체 수정). 변경할 Hub 의 존재 여부를 Hub Service 로 확인합니다.

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    - 관리자가 지정되지 않은 업체는 `MASTER` 만 수정할 수 있습니다.
                    """,
            security = @SecurityRequirement(name = "gatewayBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED), 필수 헤더 누락(MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (COMPANY_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체(COMPANY_NOT_FOUND), 존재하지 않는 Hub(HUB_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "다른 업체와 업체명 중복 (COMPANY_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Hub Service 장애 등 서버 오류 (INTERNAL_SERVER_ERROR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<CompanyResponse>> updateCompany(
            @Parameter(description = "업체 ID") UUID companyId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true,
                    description = "요청 사용자 ID (Gateway 가 JWT 의 userId 로 설정)") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true,
                    description = "요청 사용자 권한 (MASTER 또는 USER)", example = "USER") String userRole,
            UpdateCompanyRequest request);

    @Operation(
            summary = "업체 삭제",
            description = """
                    업체를 Soft Delete 합니다. 소속 상품도 모두 Soft Delete 하고,
                    삭제된 상품이 있으면 Hub Service 에 해당 상품의 재고 Soft Delete 를 요청합니다.
                    Hub 호출이 실패하면 업체·상품 삭제도 롤백됩니다.

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    """,
            security = @SecurityRequirement(name = "gatewayBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공 (data 는 null)"),
            @ApiResponse(responseCode = "400", description = "필수 헤더 누락 (MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (COMPANY_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 이미 삭제된 업체 (COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Hub 재고 삭제 실패 등 서버 오류 (INTERNAL_SERVER_ERROR, 로컬 변경 롤백)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<Void>> deleteCompany(
            @Parameter(description = "업체 ID") UUID companyId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true,
                    description = "요청 사용자 ID. 삭제자(deletedBy)로 기록") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true,
                    description = "요청 사용자 권한 (MASTER 또는 USER)", example = "MASTER") String userRole);
}
