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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyIntegrationTest extends IntegrationTestSupport {

    private static final String ROLE_MASTER = "MASTER";
    private static final String ROLE_USER = "USER";
    private static final String AFFILIATION_COMPANY = "COMPANY";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    private final UUID masterId = UUID.randomUUID();
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

    private ResultActions requestDelete(UUID companyId, String role, String affiliationType, UUID affiliationId)
            throws Exception {
        var request = delete("/companies/{companyId}", companyId)
                .header("X-User-Id", masterId)
                .header("X-User-Role", role);
        if (affiliationType != null) {
            request.header("X-User-Affiliation-Type", affiliationType);
        }
        if (affiliationId != null) {
            request.header("X-User-Affiliation-Id", affiliationId);
        }
        return mockMvc.perform(request);
    }

    private Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow();
    }

    @Nested
    @DisplayName("업체 생성")
    class Create {

        @Test
        @DisplayName("업체를 생성하면 DB에 저장되고 생성자가 X-User-Id로 기록된다")
        void create() throws Exception {
            requestCreate(masterId, "스파르타 물류", "서울시 강남구")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("업체가 생성되었습니다."))
                    .andExpect(jsonPath("$.data.name").value("스파르타 물류"))
                    .andExpect(jsonPath("$.data.userId").value(masterId.toString()));

            Company saved = companyRepository.findAll().getFirst();
            assertThat(saved.getName()).isEqualTo("스파르타 물류");
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedBy()).isEqualTo(masterId);
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
            requestDelete(deletedId, ROLE_MASTER, null, null).andExpect(status().isOk());

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
            requestDelete(deleted, ROLE_MASTER, null, null).andExpect(status().isOk());

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
            requestDelete(deletedByName, ROLE_MASTER, null, null).andExpect(status().isOk());
            requestDelete(deletedByAddress, ROLE_MASTER, null, null).andExpect(status().isOk());

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
        @DisplayName("해당 업체 소속 사용자가 수정하면 DB에 반영된다")
        void updateByCompanyMember() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");
            UUID newHubId = UUID.randomUUID();

            mockMvc.perform(put("/companies/{companyId}", companyId)
                            .header("X-User-Role", ROLE_USER)
                            .header("X-User-Affiliation-Type", AFFILIATION_COMPANY)
                            .header("X-User-Affiliation-Id", companyId)
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
        @DisplayName("다른 업체 소속 사용자가 수정하면 403을 반환하고 DB는 그대로다")
        void updateByOtherCompanyMember() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");

            mockMvc.perform(put("/companies/{companyId}", companyId)
                            .header("X-User-Role", ROLE_USER)
                            .header("X-User-Affiliation-Type", AFFILIATION_COMPANY)
                            .header("X-User-Affiliation-Id", UUID.randomUUID())
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

            requestDelete(companyId, ROLE_MASTER, null, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("업체가 삭제되었습니다."));

            Company deleted = findCompany(companyId);
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(deleted.getDeletedBy()).isEqualTo(masterId);

            mockMvc.perform(get("/companies/{companyId}", companyId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("다른 업체 소속 사용자가 삭제하면 403을 반환하고 삭제되지 않는다")
        void deleteByOtherCompanyMember() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");

            requestDelete(companyId, ROLE_USER, AFFILIATION_COMPANY, UUID.randomUUID())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("COMPANY_ACCESS_DENIED"));

            assertThat(findCompany(companyId).getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("이미 삭제된 업체를 다시 삭제하면 404를 반환한다")
        void deleteAlreadyDeleted() throws Exception {
            UUID companyId = createCompany("스파르타 물류", "서울시 강남구");
            requestDelete(companyId, ROLE_MASTER, null, null).andExpect(status().isOk());

            requestDelete(companyId, ROLE_MASTER, null, null)
                    .andExpect(status().isNotFound());
        }
    }
}
