package connect.user_service.domain.user.controller.docs;

import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.global.dto.ApiResponse;
import connect.user_service.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

// InternalUserController 의 Swagger 문서 (동작은 InternalUserController 에 있고 여기에는 설명만 둔다)
@Tag(name = "Internal User (내부 전용)", description = """
        **서비스 간 내부 호출 전용 API** (호출: company-service, order-service, delivery-service, Feign)
        - Gateway 라우트에 없어 외부 클라이언트는 호출할 수 없습니다.
        - JWT 없이 호출하며 권한 검사를 하지 않습니다.
        """)
public interface InternalUserControllerDocs {

    @Operation(
            summary = "[내부] 회원 단건 조회",
            description = """
                    company-service(업체 관리자 확인), order-service(주문자 정보) 가 호출합니다.

                    삭제된 회원은 조회되지 않습니다. (404)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 회원 (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<UserResponseDto>> sendUser(@Parameter(description = "회원 ID") UUID userId);

    @Operation(
            summary = "[내부] 허브 배송 담당자 ID 목록 조회",
            description = """
                    배송 생성 시 delivery-service 가 배송 담당자를 자동 배정하기 위해 호출합니다.

                    해당 허브 소속(`affiliationType = HUB`, `affiliationId = departureHubId`)이고 권한이 `USER` 인 회원의 ID 목록을 반환합니다.
                    해당하는 회원이 없거나 없는 허브 ID 면 빈 목록을 반환합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ApiResponse<List<UUID>> getHubDeliveryUserIds(@Parameter(description = "출발 허브 ID") UUID departureHubId);
}
