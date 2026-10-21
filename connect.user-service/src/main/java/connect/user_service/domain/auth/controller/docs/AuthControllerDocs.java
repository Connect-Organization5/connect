package connect.user_service.domain.auth.controller.docs;

import connect.user_service.domain.auth.controller.AuthController;
import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.response.UserResponseDto;
import connect.user_service.global.dto.ApiResponse;
import connect.user_service.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

// AuthController 의 Swagger 문서 (동작은 AuthController 에 있고 여기에는 설명만 둔다)
// 주의: 요청 본문의 @Valid 는 여기에만 선언한다. Hibernate Validator 는 구현 메서드가 파라미터 제약을 다시 선언하는 것을 허용하지 않는다 (HV000151)
@Tag(name = "Auth", description = "회원가입·로그인 API (토큰 없이 호출, 로그인 시 JWT 발급)")
public interface AuthControllerDocs {

    @Operation(
            summary = "회원가입",
            description = """
                    로그인 없이 누구나 가입할 수 있습니다. 권한은 항상 `USER` 로 생성되며 토큰은 발급하지 않습니다. (가입 후 `/auth/login` 으로 로그인)

                    - `username`: 4~10자, `password`: 8~15자, `email`: 이메일 형식
                    - `affiliationType` 이 `HUB` 면 hub-service, `COMPANY` 면 company-service 에서 `affiliationId` 가 존재하는지 확인합니다.
                    - `username`, `email`, `slackId` 는 중복될 수 없습니다.

                    **권한**: 없음 (인증 불필요)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락·형식 오류(VALIDATION_FAILED), JSON 파싱 실패·잘못된 소속 유형(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 허브 (HUB_NOT_FOUND), 없는 업체 (COMPANY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "아이디 중복 (USER_ALREADY_EXISTS), 이메일 중복 (EMAIL_ALREADY_EXISTS), Slack ID 중복 (SLACK_ID_ALREADY_EXISTS), 동시 가입으로 인한 중복 (DUPLICATE_RESOURCE)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "hub-service·company-service 호출 실패 (INTERNAL_SERVER_ERROR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<UserResponseDto>> signup(@Valid SignUpRequestDto requestDto);

    @Operation(
            summary = "로그인 (JWT 발급)",
            description = """
                    아이디·비밀번호를 확인하고 Access Token(JWT) 을 발급합니다.

                    - 토큰은 `data.access_token` 에 `Bearer ` 접두사 없이 담겨 있습니다. 요청 시 `Authorization: Bearer {토큰}` 으로 보냅니다.
                    - 토큰에는 `username`, `userId`, `role`, `affiliationType`, `affiliationId` 가 담기며 유효 시간은 1시간입니다.
                    - 아이디가 없거나 비밀번호가 틀려도 같은 에러를 반환합니다. (아이디 존재 여부를 노출하지 않음)

                    **권한**: 없음 (인증 불필요)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "아이디·비밀번호 누락(VALIDATION_FAILED), JSON 파싱 실패(INVALID_INPUT)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치 (INVALID_CREDENTIALS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<AuthController.AuthResponse>> login(@Valid LoginRequestDto requestDto);
}
