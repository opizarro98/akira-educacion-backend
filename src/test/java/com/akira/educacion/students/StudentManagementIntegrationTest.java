package com.akira.educacion.students;

import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.SchoolTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentManagementIntegrationTest extends SchoolTestSupport {

    private static final String PASSWORD = "Secreta123!";

    private Institution institution;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        institution = newInstitution("Colegio Central");
        newInstitutionAdmin(institution, "admin.central@test.local", PASSWORD);
        token = login("admin.central@test.local", PASSWORD);
    }

    @Test
    void creaUnEstudiante() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName":"Ana",
                          "lastName":"Pérez",
                          "documentType":"CEDULA",
                          "documentNumber":"1712345678",
                          "birthDate":"2015-05-10",
                          "admissionDate":"2026-03-01",
                          "email":"ana.perez@test.local"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Pérez"))
                .andExpect(jsonPath("$.documentNumber").value("1712345678"))
                .andExpect(jsonPath("$.status").value("PRE_REGISTERED"))
                .andExpect(jsonPath("$.birthDate").value("2015-05-10"));
    }

    @Test
    void editaUnEstudiante() throws Exception {
        String studentId = createStudent(token, "Ana", "Pérez", "1712345678", "PRE_REGISTERED");

        mockMvc.perform(put("/api/v1/students/" + studentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName":"Ana María",
                          "lastName":"Pérez",
                          "documentNumber":"1712345678",
                          "admissionDate":"2026-03-01",
                          "status":"ACTIVE",
                          "phone":"0991111111"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana María"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.phone").value("0991111111"));
    }

    @Test
    void buscaYFiltraEstudiantes() throws Exception {
        createStudent(token, "Ana", "Pérez", "1711111111", "ACTIVE");
        createStudent(token, "Luis", "Gómez", "1722222222", "PRE_REGISTERED");
        createStudent(token, "Ana", "Ruiz", "1733333333", "ACTIVE");

        mockMvc.perform(get("/api/v1/students").param("search", "ana")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/students").param("search", "Gómez")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Luis"));

        mockMvc.perform(get("/api/v1/students").param("search", "1733333333")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Ruiz"));

        mockMvc.perform(get("/api/v1/students").param("status", "PRE_REGISTERED")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Luis"));
    }

    @Test
    void rechazaIdentificacionDuplicadaEnLaMismaInstitucion() throws Exception {
        createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");

        mockMvc.perform(post("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Otro","lastName":"Estudiante","documentNumber":"1712345678",
                         "admissionDate":"2026-03-01"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void creaRepresentantes() throws Exception {
        mockMvc.perform(post("/api/v1/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullName":"María López",
                          "documentNumber":"1700000001",
                          "phone":"0999999999",
                          "email":"maria.lopez@test.local",
                          "relationship":"Madre"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("María López"))
                .andExpect(jsonPath("$.documentNumber").value("1700000001"));

        mockMvc.perform(get("/api/v1/representatives").param("search", "lópez")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void asociaVariosRepresentantesAUnEstudiante() throws Exception {
        String studentId = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");
        String motherId = createRepresentative(token, "María López", "1700000001");

        mockMvc.perform(post("/api/v1/students/" + studentId + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"representativeId":"%s","primaryContact":true,
                         "authorizedForCommunications":true,"authorizedForBilling":true}
                        """.formatted(motherId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primaryContact").value(true))
                .andExpect(jsonPath("$.authorizedForBilling").value(true));

        mockMvc.perform(post("/api/v1/students/" + studentId + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"newRepresentative":{"fullName":"Carlos Pérez","phone":"0998888888",
                          "relationship":"Padre"},
                         "primaryContact":false,"authorizedForCommunications":true,
                         "authorizedForBilling":false}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Carlos Pérez"))
                .andExpect(jsonPath("$.authorizedForBilling").value(false));

        mockMvc.perform(get("/api/v1/students/" + studentId + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].primaryContact").value(true));
    }

    @Test
    void asociaUnRepresentanteAVariosEstudiantes() throws Exception {
        String firstStudent = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");
        String secondStudent = createStudent(token, "Luis", "Pérez", "1712345679", "ACTIVE");
        String representativeId = createRepresentative(token, "María López", "1700000001");

        for (String studentId : new String[] { firstStudent, secondStudent }) {
            mockMvc.perform(post("/api/v1/students/" + studentId + "/representatives")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"representativeId":"%s","primaryContact":true}
                            """.formatted(representativeId)))
                    .andExpect(status().isCreated());
        }

        MvcResult result = mockMvc.perform(get("/api/v1/representatives/" + representativeId + "/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains("Pérez, Ana")
                .contains("Pérez, Luis");

        mockMvc.perform(get("/api/v1/representatives/" + representativeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCount").value(2));
    }

    @Test
    void elContactoPrincipalEsUnicoPorEstudiante() throws Exception {
        String studentId = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");
        String firstId = createRepresentative(token, "María López", "1700000001");
        String secondId = createRepresentative(token, "Carlos Pérez", "1700000002");

        mockMvc.perform(post("/api/v1/students/" + studentId + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"representativeId":"%s","primaryContact":true}
                        """.formatted(firstId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/students/" + studentId + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"representativeId":"%s","primaryContact":true}
                        """.formatted(secondId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/students/" + studentId + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.primaryContact == true)]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].representativeId").value(secondId));
    }
}
