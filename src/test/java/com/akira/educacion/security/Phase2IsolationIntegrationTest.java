package com.akira.educacion.security;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.SchoolTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2IsolationIntegrationTest extends SchoolTestSupport {

    private static final String PASSWORD = "Secreta123!";

    private Institution institutionA;
    private Institution institutionB;
    private String adminA;
    private String adminB;
    private String secretariaA;
    private String tesoreriaA;
    private String direccionA;

    @BeforeEach
    void setUp() throws Exception {
        institutionA = newInstitution("Institución A");
        institutionB = newInstitution("Institución B");
        newInstitutionAdmin(institutionA, "admin.a@test.local", PASSWORD);
        newInstitutionAdmin(institutionB, "admin.b@test.local", PASSWORD);
        newUser(institutionA, "secretaria.a@test.local", PASSWORD, RoleCodes.SECRETARY);
        newUser(institutionA, "tesoreria.a@test.local", PASSWORD, RoleCodes.TREASURY);
        newUser(institutionA, "direccion.a@test.local", PASSWORD, RoleCodes.DIRECTOR);
        newPlatformAdmin("platform@test.local", PASSWORD);

        adminA = login("admin.a@test.local", PASSWORD);
        adminB = login("admin.b@test.local", PASSWORD);
        secretariaA = login("secretaria.a@test.local", PASSWORD);
        tesoreriaA = login("tesoreria.a@test.local", PASSWORD);
        direccionA = login("direccion.a@test.local", PASSWORD);
    }

    @Test
    void unaInstitucionNoAccedeAEstudiantesDeOtra() throws Exception {
        String studentB = createStudent(adminB, "Luis", "Ajeno", "1799999999", "ACTIVE");

        mockMvc.perform(get("/api/v1/students/" + studentB)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/students")
                .param("institutionId", institutionB.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void noPermiteReferenciasCruzadasEnLaApi() throws Exception {
        String studentA = createStudent(adminA, "Ana", "Propia", "1711111111", "ACTIVE");
        String studentB = createStudent(adminB, "Luis", "Ajeno", "1799999999", "ACTIVE");
        String levelA = createLevel(adminA, "Nivel A");
        String representativeB = createRepresentative(adminB, "Representante Ajeno", "1788888888");

        mockMvc.perform(post("/api/v1/enrollments")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","educationalLevelId":"%s","startDate":"2026-03-01","agreedPrice":50.00}
                        """.formatted(studentB, levelA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/students/" + studentA + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"representativeId":"%s","primaryContact":true}
                        """.formatted(representativeB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void laBaseDeDatosRechazaReferenciasCruzadas() throws Exception {
        String studentA = createStudent(adminA, "Ana", "Propia", "1711111111", "ACTIVE");
        String levelB = createLevel(adminB, "Nivel B");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO enrollment
                    (id, institution_id, student_id, educational_level_id, start_date, agreed_price,
                     status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
                """,
                UUID.randomUUID(), institutionA.getId(), UUID.fromString(studentA), UUID.fromString(levelB),
                Date.valueOf(LocalDate.of(2026, 3, 1)), new BigDecimal("50.00"), "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secretariaGestionaEstudiantesYTesoreriaNo() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretariaA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Ana","lastName":"Pérez","documentNumber":"1712345678",
                         "admissionDate":"2026-03-01","notes":"Observación interna"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(tesoreriaA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Luis","lastName":"Gómez","admissionDate":"2026-03-01"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasObservacionesDelEstudianteEstanProtegidas() throws Exception {
        String studentId = createStudentWithNotes("Observación reservada");

        mockMvc.perform(get("/api/v1/students/" + studentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tesoreriaA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.notesRestricted").value(true));

        mockMvc.perform(get("/api/v1/students/" + studentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(direccionA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Observación reservada"))
                .andExpect(jsonPath("$.notesRestricted").value(false));
    }

    @Test
    void tesoreriaYDireccionNoAdministranElCatalogo() throws Exception {
        for (String token : new String[] { tesoreriaA, direccionA }) {
            mockMvc.perform(post("/api/v1/catalog/levels")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"Nivel no permitido"}
                            """))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/v1/catalog/levels")
                .header(HttpHeaders.AUTHORIZATION, bearer(tesoreriaA)))
                .andExpect(status().isOk());
    }

    @Test
    void soloQuienTienePermisoDeMatriculaPuedeCrear() throws Exception {
        creatBaseForEnrollment();

        mockMvc.perform(post("/api/v1/enrollments")
                .header(HttpHeaders.AUTHORIZATION, bearer(direccionA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","educationalLevelId":"%s","startDate":"2026-03-01","agreedPrice":50.00}
                        """.formatted(studentId, levelId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/enrollments")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretariaA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","educationalLevelId":"%s","startDate":"2026-03-01","agreedPrice":50.00}
                        """.formatted(studentId, levelId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/enrollments")
                .header(HttpHeaders.AUTHORIZATION, bearer(direccionA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void platformAdminRequiereInstitucionExplicitaYDejaAuditoria() throws Exception {
        createStudent(adminA, "Ana", "Propia", "1711111111", "ACTIVE");
        String platform = login("platform@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(platform)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/students")
                .param("institutionId", institutionA.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(platform)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        Integer events = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where action = 'PLATFORM_GLOBAL_ACCESS'", Integer.class);
        assertThat(events).isNotNull().isGreaterThan(0);
    }

    private String studentId;
    private String levelId;

    private void creatBaseForEnrollment() throws Exception {
        studentId = createStudent(adminA, "Ana", "Propia", "1711111111", "ACTIVE");
        levelId = createLevel(adminA, "Nivel A");
    }

    private String createStudentWithNotes(String notes) throws Exception {
        var result = mockMvc.perform(post("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Ana","lastName":"Pérez","admissionDate":"2026-03-01","notes":"%s"}
                        """.formatted(notes)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }
}
