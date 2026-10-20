package connect.user_service.domain.auth.controller;

import connect.user_service.domain.client.clientinterface.CompanyClient;
import connect.user_service.domain.client.clientinterface.HubClient;
import connect.user_service.domain.user.dto.response.CompanyInfo;
import connect.user_service.domain.user.dto.response.HubInfo;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 Security 필터 체인 + H2 DB 로 로그인 없이 회원가입 → 로그인까지 확인
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");

    private static final String VALID_BODY = """
            {
              "username": "tester",
              "password": "password1!",
              "email": "tester@test.com",
              "affiliationType": "COMPANY",
              "affiliationId": "00000000-0000-0000-0000-000000001000",
              "slackId": "slack-1"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 테스트에서 실제 Company / Hub Service 를 호출하지 않도록 Mock 으로 교체 (setUp 에서 기본은 조회 성공으로 설정)
    @MockitoBean
    private CompanyClient companyClient;

    @MockitoBean
    private HubClient hubClient;

    @BeforeEach
    void setUp() {
        given(companyClient.getCompany(AFFILIATION_ID))
                .willReturn(new CompanyInfo(AFFILIATION_ID, null, UUID.randomUUID(), "커넥트상사", "서울특별시 강남구 테헤란로 1"));
        given(hubClient.getHub(AFFILIATION_ID))
                .willReturn(new HubInfo(AFFILIATION_ID, "서울특별시 센터", "서울특별시 송파구 송파대로 55"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("토큰 없이 회원가입하면 201, DB 에 USER 권한과 암호화된 비밀번호로 저장")
    void signup_withoutToken() throws Exception {
        postJson("/auth/signup", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.username").value("tester"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        User saved = userRepository.findByUsername("tester").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getAffiliationType()).isEqualTo(AffiliationType.COMPANY);
        assertThat(saved.getAffiliationId()).isEqualTo(AFFILIATION_ID);
        assertThat(saved.getPassword()).isNotEqualTo("password1!");
        assertThat(passwordEncoder.matches("password1!", saved.getPassword())).isTrue();
        assertThat(saved.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("요청 본문에 role 을 MASTER 로 넣어도 USER 로 저장 (스스로 권한 상승 불가)")
    void signup_cannotChooseRole() throws Exception {
        String body = VALID_BODY.replace("\"slackId\": \"slack-1\"", "\"slackId\": \"slack-1\", \"role\": \"MASTER\"");

        postJson("/auth/signup", body)
                .andExpect(status().isCreated());

        assertThat(userRepository.findByUsername("tester").orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("가입한 아이디, 비밀번호로 바로 로그인하면 200 과 access_token 발급")
    void signup_thenLogin() throws Exception {
        postJson("/auth/signup", VALID_BODY)
                .andExpect(status().isCreated());

        postJson("/auth/login", """
                { "username": "tester", "password": "password1!" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.access_token").value(not(emptyOrNullString())));
    }

    @Test
    @DisplayName("같은 username 으로 다시 가입하면 409 USER_ALREADY_EXISTS")
    void signup_duplicate() throws Exception {
        postJson("/auth/signup", VALID_BODY)
                .andExpect(status().isCreated());

        postJson("/auth/signup", VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("소속 업체가 Company Service 에 없으면 404 COMPANY_NOT_FOUND, DB 에 저장하지 않음")
    void signup_companyNotFound() throws Exception {
        given(companyClient.getCompany(AFFILIATION_ID)).willThrow(new BaseException(ErrorCode.COMPANY_NOT_FOUND));

        postJson("/auth/signup", VALID_BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));

        assertThat(userRepository.findByUsername("tester")).isEmpty();
    }

    @Test
    @DisplayName("소속 허브가 Hub Service 에 없으면 404 HUB_NOT_FOUND, DB 에 저장하지 않음")
    void signup_hubNotFound() throws Exception {
        given(hubClient.getHub(AFFILIATION_ID)).willThrow(new BaseException(ErrorCode.HUB_NOT_FOUND));

        postJson("/auth/signup", VALID_BODY.replace("\"COMPANY\"", "\"HUB\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUB_NOT_FOUND"));

        assertThat(userRepository.findByUsername("tester")).isEmpty();
    }
}
