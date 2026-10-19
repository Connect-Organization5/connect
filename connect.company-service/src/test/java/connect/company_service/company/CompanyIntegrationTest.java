package connect.company_service.company;

import com.jayway.jsonpath.JsonPath;
import connect.company_service.company.entity.Company;
import connect.company_service.company.repository.CompanyRepository;
import connect.company_service.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyIntegrationTest extends IntegrationTestSupport {

    private static final String ROLE_MASTER = "MASTER";
    private static final String ROLE_USER = "USER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    private final UUID masterId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID hubId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        companyRepository.deleteAll();
    }

    private String body(UUID hubId, String name, String address) {
        return """
                {"hubId": "%s", "name": "%s", "address": "%s"}
                """.formatted(hubId, name, address);
    }

    private ResultActions requestCreate(UUID userId, String name, String address) throws Exception {
        return mockMvc.perform(post("/companies")
                .header("X-User-Id", userId)
                .header("X-User-Role", ROLE_MASTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(hubId, name, address)));
    }

    private UUID createCompany(String name, String address) throws Exception {
        String response = requestCreate(masterId, name, address)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.data.id"));
    }

    // USER(ownerId)가 직접 생성해 본인이 관리자인 업체
    private UUID createOwnedCompany(String name, String address) throws Exception {
        String response = mockMvc.perform(post("/companies")
                        .header("X-User-Id", ownerId)
                        .header("X-User-Role", ROLE_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(hubId, name, address)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.data.id"));
    }

    private ResultActions requestDelete(UUID companyId, UUID userId, String role) throws Exception {
        return mockMvc.perform(delete("/companies/{companyId}", companyId)
                .header("X-User-Id", userId)
                .header("X-User-Role", role));
    }

    private Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow();
    }

    @Nested
    @DisplayName("업체 생성")
    class Create {

        @Test
        @DisplayName("MASTER가 생성하면 관리자 없이(userId null) 저장되고 생성자는 X-User-Id로 기록된다")
        void createByMaster() throws Exception {
            requestCreate(masterId, "스파르타 물류", "서울시 강남구")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("업체가 생성되었습니다."))
                    .andExpect(jsonPath("$.data.name").value("스파르타 물류"))
                    .andExpect(jsonPath("$.data.userId").value(nullValue()));

            Company saved = companyRepository.findAll().getFirst();
            assertThat(saved.getName()).isEqualTo("스파르타 물류");
            assertThat(saved.getUserId()).isNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedBy()).isEqualTo(masterId);
        }

        @Test
        @DisplayName("USER가 생성하면 본인이 관리자(userId)로 저장된다")
        void createByUser() throws Exception {
            UUID companyId = createOwnedCompany("스파르타 물류", "서울시 강남구");

            assertThat(findCompany(companyId).getUserId()).isEqualTo(ownerId);
        }

        @Test
        @DisplayName("삭제되지 않은 업체와 이름이 같으면 409를 반환한다")
        void createDuplicateName() throws Exception {
            createCompany("스파르타 물류", "서울시 강남구");

            requestCreate(masterId, "스파르타 물류", "부산시 해운대구")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("삭제된 업체와 이름이 같으면 새로 생성할 수 있다")
        void createWithDeletedCompanyName() throws Exception {
            UUID deletedId = createCompany("스파르타 물류", "서울시 강남구");
            requestDelete(deletedId, masterId, ROLE_MASTER).andExpect(status().isOk());

            requestCreate(masterId, "스파르타 물류", "부산시 해운대구")
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("필수 헤더가 없으면 400 MISSING_HEADER를 반환한다")
        void createWithoutHeader() throws Exception {
            mockMvc.perform(post("/companies")
                            .header("X-User-Role", ROLE_MASTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(hubId, "스파르타 물류", "서울시 강남구")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_HEADER"));

            assertThat(companyRepository.count()).isZero();
        }

        @Test
        @DisplayName("요청 값이 유효하지 않으면 400 VALIDATION_FAILED를 반환한다")
        void createWithInvalidBody() throws Exception {
            requestCreate(masterId, "", "서울시 강남구")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("업체명은 필수입니다."));

            assertThat(companyRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("업체 조회 및 검색")
    class Read {

        @Test
        @DisplayName("업체를 단건 조회한다")
        void getCompany() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");

            mockMvc.perform(get("/companies/{companyId}", companyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(companyId.toString()))
                    .andExpect(jsonPath("$.data.address").value("서울시 강남구"));
        }

        @Test
        @DisplayName("없는 업체를 조회하면 404를 반환한다")
        void getCompanyNotFound() throws Exception {
            mockMvc.perform(get("/companies/{companyId}", UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
        }

        @Test
        @DisplayName("목록은 삭제된 업체를 빼고 생성일 역순으로 조회한다")
        void getCompanies() throws Exception {
            UUID first = createCompany("스파르타 물류", "서울시 강남구");
            UUID second = createCompany("한빛 상사", "부산시 해운대구");
            UUID deleted = createCompany("삭제될 업체", "대구시 중구");
            requestDelete(deleted, masterId, ROLE_MASTER).andExpect(status().isOk());

            mockMvc.perform(get("/companies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.contents[0].id").value(second.toString()))
                    .andExpect(jsonPath("$.data.contents[1].id").value(first.toString()));
        }

        @Test
        @DisplayName("검색어로 업체명 또는 주소를 검색하고 삭제된 업체는 제외한다")
        void searchCompanies() throws Exception {
            UUID byName = createCompany("스파르타 물류", "서울시 강남구");
            UUID byAddress = createCompany("한빛 상사", "부산시 스파르타로");
            createCompany("관계없는 업체", "대구시 중구");
            UUID deletedByName = createCompany("스파르타 삭제 업체", "광주시 북구");
            UUID deletedByAddress = createCompany("삭제 업체", "인천시 스파르타동");
            requestDelete(deletedByName, masterId, ROLE_MASTER).andExpect(status().isOk());
            requestDelete(deletedByAddress, masterId, ROLE_MASTER).andExpect(status().isOk());

            mockMvc.perform(get("/companies").param("keyword", " 스파르타 "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.contents", hasSize(2)))
                    .andExpect(jsonPath("$.data.contents[0].id").value(byAddress.toString()))
                    .andExpect(jsonPath("$.data.contents[1].id").value(byName.toString()));
        }
    }

    @Nested
    @DisplayName("업체 수정")
    class Update {

        @Test
        @DisplayName("업체를 생성한 사용자가 수정하면 DB에 반영된다")
        void updateByOwner() throws Exception {
            UUID companyId = createOwnedCompany("스파르타 물류", "서울시 강남구");
            UUID newHubId = UUID.randomUUID();

            mockMvc.perform(put("/companies/{companyId}", companyId)
                            .header("X-User-Id", ownerId)
                            .header("X-User-Role", ROLE_USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(newHubId, "스파르타 택배", "부산시 해운대구")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("스파르타 택배"));

            Company updated = findCompany(companyId);
            assertThat(updated.getHubId()).isEqualTo(newHubId);
            assertThat(updated.getName()).isEqualTo("스파르타 택배");
            assertThat(updated.getAddress()).isEqualTo("부산시 해운대구");
        }

        @Test
        @DisplayName("관리자가 지정되지 않은 업체를 USER가 수정하면 403을 반환하고 DB는 그대로다")
        void updateUnassignedByUser() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");

            mockMvc.perform(put("/companies/{companyId}", companyId)
                            .header("X-User-Id", ownerId)
                            .header("X-User-Role", ROLE_USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(hubId, "스파르타 택배", "부산시 해운대구")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("COMPANY_ACCESS_DENIED"));

            assertThat(findCompany(companyId).getName()).isEqualTo("스파르타 물류");
        }

        @Test
        @DisplayName("업체를 생성한 사용자가 아니면 수정 시 403을 반환하고 DB는 그대로다")
        void updateByNonOwner() throws Exception {
            UUID companyId = createOwnedCompany("스파르타 물류", "서울시 강남구");

            mockMvc.perform(put("/companies/{companyId}", companyId)
                            .header("X-User-Id", UUID.randomUUID())
                            .header("X-User-Role", ROLE_USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(hubId, "스파르타 택배", "부산시 해운대구")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("COMPANY_ACCESS_DENIED"));

            assertThat(findCompany(companyId).getName()).isEqualTo("스파르타 물류");
        }
    }

    @Nested
    @DisplayName("업체 삭제")
    class Delete {

        @Test
        @DisplayName("삭제하면 행은 남고 deletedAt, deletedBy가 기록되며 이후 조회는 404다")
        void delete() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");

            requestDelete(companyId, masterId, ROLE_MASTER)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("업체가 삭제되었습니다."));

            Company deleted = findCompany(companyId);
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(deleted.getDeletedBy()).isEqualTo(masterId);

            mockMvc.perform(get("/companies/{companyId}", companyId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("업체를 생성한 사용자가 아니면 삭제 시 403을 반환하고 삭제되지 않는다")
        void deleteByNonOwner() throws Exception {
            UUID companyId = createOwnedCompany("스파르타 물류", "서울시 강남구");

            requestDelete(companyId, UUID.randomUUID(), ROLE_USER)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("COMPANY_ACCESS_DENIED"));

            assertThat(findCompany(companyId).getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("이미 삭제된 업체를 다시 삭제하면 404를 반환한다")
        void deleteAlreadyDeleted() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");
            requestDelete(companyId, masterId, ROLE_MASTER).andExpect(status().isOk());

            requestDelete(companyId, masterId, ROLE_MASTER)
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("회원가입 시 기존 업체에 관리자 지정 (내부 API)")
    class AssignManager {

        // 회원가입 중 호출이라 사용자 헤더 없이 요청한다
        private ResultActions requestAssign(UUID companyId, UUID managerId) throws Exception {
            return mockMvc.perform(patch("/internal/companies/{companyId}/manager", companyId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"userId": "%s"}
                            """.formatted(managerId)));
        }

        @Test
        @DisplayName("관리자가 없는 업체에 지정하면 DB에 반영되고, 지정된 사용자는 업체를 수정할 수 있다")
        void assign() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");

            requestAssign(companyId, ownerId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("업체 연결이 처리되었습니다."))
                    .andExpect(jsonPath("$.data.userId").value(ownerId.toString()));

            assertThat(findCompany(companyId).getUserId()).isEqualTo(ownerId);

            mockMvc.perform(put("/companies/{companyId}", companyId)
                            .header("X-User-Id", ownerId)
                            .header("X-User-Role", ROLE_USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(hubId, "스파르타 택배", "부산시 해운대구")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("이미 관리자가 있으면 200을 반환하고 기존 관리자가 유지된다 (두 번째 직원 가입)")
        void assignAlreadyAssigned() throws Exception {
            UUID companyId = createOwnedCompany("스파르타 물류", "서울시 강남구");

            requestAssign(companyId, UUID.randomUUID())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(ownerId.toString()));

            assertThat(findCompany(companyId).getUserId()).isEqualTo(ownerId);
        }

        @Test
        @DisplayName("삭제된 업체면 404를 반환한다")
        void assignDeletedCompany() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");
            requestDelete(companyId, masterId, ROLE_MASTER).andExpect(status().isOk());

            requestAssign(companyId, ownerId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
        }

        @Test
        @DisplayName("userId가 없으면 400 VALIDATION_FAILED를 반환한다")
        void assignWithoutUserId() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");

            mockMvc.perform(patch("/internal/companies/{companyId}/manager", companyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("사용자 ID는 필수입니다."));
        }
    }

    @Nested
    @DisplayName("회원가입 시 업체 생성 (내부 API)")
    class CreateForSignUp {

        private ResultActions requestCreateForSignUp(UUID userId, String name) throws Exception {
            return mockMvc.perform(post("/internal/companies")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"userId": "%s", "hubId": "%s", "name": "%s", "address": "서울시 강남구"}
                            """.formatted(userId, hubId, name)));
        }

        @Test
        @DisplayName("사용자 헤더 없이 생성되고 가입하는 사용자가 관리자로 저장되며, 그 사용자는 업체를 수정할 수 있다")
        void createForSignUp() throws Exception {
            String response = requestCreateForSignUp(ownerId, "스파르타 물류")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("업체가 생성되었습니다."))
                    .andExpect(jsonPath("$.data.userId").value(ownerId.toString()))
                    .andReturn().getResponse().getContentAsString();
            UUID companyId = UUID.fromString(JsonPath.read(response, "$.data.id"));

            assertThat(findCompany(companyId).getUserId()).isEqualTo(ownerId);

            mockMvc.perform(put("/companies/{companyId}", companyId)
                            .header("X-User-Id", ownerId)
                            .header("X-User-Role", ROLE_USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(hubId, "스파르타 택배", "부산시 해운대구")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("삭제되지 않은 업체와 이름이 같으면 409를 반환한다")
        void createForSignUpWithDuplicateName() throws Exception {
            createCompany("스파르타 물류", "서울시 강남구");

            requestCreateForSignUp(ownerId, "스파르타 물류")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("userId가 없으면 400 VALIDATION_FAILED를 반환하고 저장하지 않는다")
        void createForSignUpWithoutUserId() throws Exception {
            mockMvc.perform(post("/internal/companies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"hubId": "%s", "name": "스파르타 물류", "address": "서울시 강남구"}
                                    """.formatted(hubId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("사용자 ID는 필수입니다."));

            assertThat(companyRepository.count()).isZero();
        }
    }
}
