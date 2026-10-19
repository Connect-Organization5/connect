package connect.user_service.domain.auth.controller;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
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
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.password").doesNotExist());

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
                .andExpect(jsonPath("$.access_token").value(not(emptyOrNullString())));
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
}
