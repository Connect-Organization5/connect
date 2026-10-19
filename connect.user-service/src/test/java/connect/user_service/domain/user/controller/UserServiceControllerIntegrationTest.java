package connect.user_service.domain.user.controller;

import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 Security 필터 체인 + H2 DB 로 저장된 회원이 userId 로 조회되는지 끝까지 확인
// /internal/** 은 서비스 간 내부 호출이라 JWT 없이 호출
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserServiceControllerIntegrationTest {

    private static final String URL = "/internal/users/{userId}";
    private static final UUID AFFILIATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User saved;

    @BeforeEach
    void setUp() {
        saved = userRepository.saveAndFlush(new User("tester", "encodedPw", "tester@test.com", UserRole.USER,
                AffiliationType.COMPANY, AFFILIATION_ID, "slack-1"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("JWT 없이 DB 에 저장된 회원을 userId 로 조회하면 200 과 해당 회원 정보 반환")
    void sendUser_returnsSavedUserWithoutToken() throws Exception {
        mockMvc.perform(get(URL, saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.email").value("tester@test.com"))
                .andExpect(jsonPath("$.affiliationType").value("COMPANY"))
                .andExpect(jsonPath("$.affiliationId").value(AFFILIATION_ID.toString()))
                .andExpect(jsonPath("$.slackId").value("slack-1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("잘못된 토큰이 붙어 와도 내부 호출은 인증을 요구하지 않으므로 200")
    void sendUser_invalidTokenIgnored() throws Exception {
        mockMvc.perform(get(URL, saved.getId()).header(JwtUtil.AUTHORIZATION_HEADER, JwtUtil.BEARER_PREFIX + "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()));
    }

    @Test
    @DisplayName("없는 userId 면 404 USER_NOT_FOUND")
    void sendUser_notFound() throws Exception {
        mockMvc.perform(get(URL, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("논리적으로 삭제된 회원이면 404 USER_NOT_FOUND")
    void sendUser_softDeleted() throws Exception {
        User other = userRepository.saveAndFlush(new User("deleted", "encodedPw", "deleted@test.com", UserRole.USER,
                AffiliationType.COMPANY, AFFILIATION_ID, "slack-deleted"));
        // softDeleteById 는 UPDATE 쿼리라 트랜잭션 안에서 실행해야 함 (실제로는 UserService.deleteUser 의 @Transactional 안에서 호출)
        transactionTemplate.executeWithoutResult(status ->
                userRepository.softDeleteById(other.getId(), saved.getId(), LocalDateTime.now()));

        mockMvc.perform(get(URL, other.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("내부 경로가 아닌 /users/{userId} 는 여전히 토큰 없으면 401")
    void publicUserApi_stillRequiresToken() throws Exception {
        mockMvc.perform(get("/users/{userId}", saved.getId()))
                .andExpect(status().isUnauthorized());
    }
}
