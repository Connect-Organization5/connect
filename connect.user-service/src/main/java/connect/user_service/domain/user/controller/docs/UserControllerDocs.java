package connect.user_service.domain.user.controller.docs;

import connect.user_service.domain.user.dto.UserInfoDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.global.dto.ApiResponse;
import connect.user_service.global.dto.PageResponse;
import connect.user_service.global.exception.ErrorResponse;
import connect.user_service.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static connect.user_service.global.config.OpenApiConfig.GATEWAY_AUTH;

// UserController 의 Swagger 문서 (동작은 UserController 에 있고 여기에는 설명만 둔다)
// 요청자는 Authorization 헤더의 JWT 로 인증된 회원 (@AuthenticationPrincipal) 이므로 문서에서는 숨긴다
// 주의: page·size 의 @Min/@Max 와 요청 본문의 @Valid 는 여기에만 선언한다. Hibernate Validator 는 구현 메서드가 파라미터 제약을 다시 선언하는 것을 허용하지 않는다 (HV000151)
@Tag(name = "User", description = "회원 관리 API (JWT 필요, User Service 가 Authorization 헤더를 직접 검증)")
public interface UserControllerDocs {

    @Operation(
            summary = "회원 단건 조회",
            description = """
                    **권한**: 본인 또는 `MASTER`

                    `USER` 가 다른 회원을 조회하면 대상 회원의 존재 여부와 관계없이 403 으로 응답합니다.
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음 (UNAUTHORIZED), 유효하지 않거나 만료된 토큰 (INVALID_TOKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인도 MASTER 도 아님 (USER_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 회원 (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<UserResponseDto>> getUser(
            @Parameter(description = "회원 ID") UUID userId,
            @Parameter(hidden = true) UserDetailsImpl userDetails);

    @Operation(
            summary = "회원 목록 조회",
            description = """
                    소속과 관계없이 삭제되지 않은 모든 회원을 조회합니다.

                    **정렬**: `MASTER` 먼저 → 가입일(`createdAt`) 내림차순 (고정)

                    **페이징**: `page` 는 0 이상 (기본 0), `size` 는 1~100 (기본 10)

                    **권한**: `MASTER`
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "page·size 범위 오류(VALIDATION_FAILED), 숫자가 아님(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음 (UNAUTHORIZED), 유효하지 않거나 만료된 토큰 (INVALID_TOKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "MASTER 가 아님 (USER_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<PageResponse<UserResponseDto>>> getAllUsers(
            @Parameter(in = ParameterIn.QUERY, description = "페이지 번호 (0부터 시작)", example = "0")
            @Min(value = 0, message = "page 는 0 이상이어야 합니다.") int page,
            @Parameter(in = ParameterIn.QUERY, description = "페이지 크기 (1~100)", example = "10")
            @Min(value = 1, message = "size 는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size 는 100 이하여야 합니다.") int size,
            @Parameter(hidden = true) UserDetailsImpl userDetails);

    @Operation(
            summary = "회원 정보 수정",
            description = """
                    PUT 방식이므로 `email`, `password`, `slackId`, `role` 을 모두 보내야 합니다.

                    - `username` 과 소속(`affiliationType`, `affiliationId`)은 변경할 수 없습니다. 소속 필드를 보내면 400 입니다.
                    - 비밀번호는 8~15자이며 BCrypt 로 암호화해 저장합니다.
                    - 마지막 `MASTER` 를 `USER` 로 바꿀 수 없습니다.

                    **권한**: `MASTER` (본인 포함 모든 회원)
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "필수값 누락·형식 오류·소속 변경 시도(VALIDATION_FAILED), UUID 형식 오류·JSON 파싱 실패·잘못된 role(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음 (UNAUTHORIZED), 유효하지 않거나 만료된 토큰 (INVALID_TOKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "MASTER 가 아님 (USER_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 삭제된 회원 (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "마지막 MASTER 강등 (LAST_MASTER_CANNOT_BE_DEMOTED), 이메일 중복 (EMAIL_ALREADY_EXISTS), Slack ID 중복 (SLACK_ID_ALREADY_EXISTS), "
                            + "동시 수정으로 인한 중복 (DUPLICATE_RESOURCE), 다른 MASTER 변경 요청 처리 중 (LOCK_CONFLICT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<UserResponseDto>> updateUser(
            @Parameter(description = "수정할 회원 ID") UUID userId,
            @Valid UpdateUserRequestDto requestDto,
            @Parameter(hidden = true) UserDetailsImpl userDetails);

    @Operation(
            summary = "회원 삭제",
            description = """
                    논리적 삭제입니다. (`deletedAt`, `deletedBy` 기록) 삭제된 회원은 조회·로그인 인증에서 제외되고, 기존 토큰도 401(`INVALID_TOKEN`) 이 됩니다.

                    마지막 `MASTER` 는 삭제할 수 없습니다.

                    **권한**: `MASTER` (본인 포함 모든 회원)
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공 (본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 (INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음 (UNAUTHORIZED), 유효하지 않거나 만료된 토큰 (INVALID_TOKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "MASTER 가 아님 (USER_ACCESS_DENIED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없거나 이미 삭제된 회원 (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "마지막 MASTER 삭제 (LAST_MASTER_CANNOT_BE_DELETED), 다른 MASTER 변경 요청 처리 중 (LOCK_CONFLICT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteUser(
            @Parameter(description = "삭제할 회원 ID") UUID userId,
            @Parameter(hidden = true) UserDetailsImpl userDetails);

    @Operation(
            summary = "로그인 회원 정보 조회",
            description = """
                    토큰의 회원 `username` 과 관리자 여부(`isAdmin`, `MASTER` 면 true)를 반환합니다.

                    **권한**: 로그인한 모든 회원
                    """,
            security = @SecurityRequirement(name = GATEWAY_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "토큰 없음 (UNAUTHORIZED), 유효하지 않거나 만료된 토큰 (INVALID_TOKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<UserInfoDto>> getUserInfo(@Parameter(hidden = true) UserDetailsImpl userDetails);
}
