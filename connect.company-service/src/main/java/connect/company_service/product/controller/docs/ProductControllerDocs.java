package connect.company_service.product.controller.docs;

import connect.company_service.global.dto.ErrorResponse;
import connect.company_service.global.dto.PagedResponse;
import connect.company_service.product.dto.ProductCreateRequest;
import connect.company_service.product.dto.ProductResponse;
import connect.company_service.product.dto.ProductUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

// ProductController 의 Swagger 문서 (동작은 ProductController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Product", description = "업체별 상품 관리 API (등록·수정·삭제는 X-User-Id / X-User-Role 헤더 필요)")
public interface ProductControllerDocs {

    @Operation(
            summary = "상품 등록",
            description = """
                    업체에 상품을 등록합니다.

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    """,
            security = @SecurityRequirement(name = "gatewayBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED), 필수 헤더 누락(MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (COMPANY_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체 (COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<ProductResponse>> createProduct(
            @Parameter(description = "업체 ID") UUID companyId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true,
                    description = "요청 사용자 ID (Gateway 가 JWT 의 userId 로 설정)") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true,
                    description = "요청 사용자 권한 (MASTER 또는 USER)", example = "USER") String role,
            ProductCreateRequest request);

    @Operation(
            summary = "상품 단건 조회",
            description = """
                    업체에 속한 삭제되지 않은 상품을 조회합니다. 다른 업체의 상품 ID 면 404 입니다.

                    **권한**: 별도 권한 검사 없음 (사용자 헤더 불필요)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체(COMPANY_NOT_FOUND), 없거나 다른 업체의 상품(PRODUCT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<ProductResponse>> getProduct(
            @Parameter(description = "업체 ID") UUID companyId,
            @Parameter(description = "상품 ID") UUID productId);

    @Operation(
            summary = "상품 목록 조회 및 검색",
            description = """
                    업체의 삭제되지 않은 상품을 조회합니다. `name` 이 있으면 상품명 부분 일치 검색입니다.

                    - 페이징 기본값: `page=0`, `size=10`, `sort=createdAt,desc`
                    - `size` 는 최대 100
                    - 정렬 예: `sort=price,asc`, `sort=name,desc` (없는 필드면 400)

                    **권한**: 별도 권한 검사 없음 (사용자 헤더 불필요)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (data.contents 에 목록)"),
            @ApiResponse(responseCode = "400", description = "size > 100 또는 존재하지 않는 정렬 필드 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체 (COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<PagedResponse<ProductResponse>>> getProducts(
            @Parameter(description = "업체 ID") UUID companyId,
            @Parameter(description = "상품명 검색어 (부분 일치, 생략 시 전체)", example = "사과") String name,
            @ParameterObject Pageable pageable);

    @Operation(
            summary = "상품 수정",
            description = """
                    상품명, 설명, 가격을 수정합니다(PUT 전체 수정). 소속 업체는 변경할 수 없습니다.

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    """,
            security = @SecurityRequirement(name = "gatewayBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED), 필수 헤더 누락(MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (COMPANY_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체(COMPANY_NOT_FOUND), 없거나 다른 업체의 상품(PRODUCT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<ProductResponse>> updateProduct(
            @Parameter(description = "업체 ID") UUID companyId,
            @Parameter(description = "상품 ID") UUID productId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true,
                    description = "요청 사용자 ID (Gateway 가 JWT 의 userId 로 설정)") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true,
                    description = "요청 사용자 권한 (MASTER 또는 USER)", example = "USER") String role,
            ProductUpdateRequest request);

    @Operation(
            summary = "상품 삭제",
            description = """
                    상품을 Soft Delete 하고, Hub Service 에 해당 상품의 재고 Soft Delete 를 요청합니다.
                    Hub 호출이 실패하면 상품 삭제도 롤백됩니다.

                    **권한**: `MASTER` 또는 해당 업체 관리자(`Company.userId == X-User-Id`)
                    """,
            security = @SecurityRequirement(name = "gatewayBearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공 (message: \"삭제되었습니다.\", data 는 null)"),
            @ApiResponse(responseCode = "400", description = "필수 헤더 누락 (MISSING_HEADER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "MASTER 도 업체 관리자도 아님 (COMPANY_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없거나 삭제된 업체(COMPANY_NOT_FOUND), 없거나 이미 삭제된 상품(PRODUCT_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Hub 재고 삭제 실패 등 서버 오류 (INTERNAL_SERVER_ERROR, 로컬 변경 롤백)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<connect.company_service.global.dto.ApiResponse<Void>> deleteProduct(
            @Parameter(description = "업체 ID") UUID companyId,
            @Parameter(description = "상품 ID") UUID productId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", required = true,
                    description = "요청 사용자 ID. 삭제자(deletedBy)로 기록되고 Hub 재고 삭제 요청에도 전달") UUID userId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Role", required = true,
                    description = "요청 사용자 권한 (MASTER 또는 USER)", example = "USER") String role);
}
