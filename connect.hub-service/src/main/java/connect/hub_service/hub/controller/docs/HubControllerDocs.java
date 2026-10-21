package connect.hub_service.hub.controller.docs;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.hub.dto.HubResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

import static connect.hub_service.global.config.OpenApiConfig.GATEWAY_AUTH;

// HubController 의 Swagger 문서 (동작은 HubController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Hub", description = "허브 조회 API")
public interface HubControllerDocs {

    @Operation(
            summary = "허브 목록 조회·검색",
            description = """
                    `keyword` 가 있으면 허브 이름 또는 주소 부분 일치 검색입니다. 이름 → ID 순으로 정렬합니다.

                    **페이징**: `page` 는 0 이상, `size` 는 1~100

                    **권한**: 별도 권한 검사 없음
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "페이지 번호·크기 범위 오류(INVALID_PAGE), 형식 오류(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<PagedResponse<HubResponse>> getHubs(
            @Parameter(description = "허브 이름·주소 검색어 (부분 일치, 생략 시 전체)", example = "서울") String keyword,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "10") int size);

    @Operation(
            summary = "허브 단건 조회",
            description = "**권한**: 별도 권한 검사 없음",
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 허브 (HUB_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    ApiResponse<HubResponse> getHub(@Parameter(description = "허브 ID") UUID hubId);
}
