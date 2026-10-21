package com.akira.educacion.enrollment;

import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.SchoolTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentIntegrationTest extends SchoolTestSupport {

    private static final String PASSWORD = "Secreta123!";

    private String token;
    private String studentId;
    private String levelId;
    private String periodId;
    private String planId;
    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        Institution institution = newInstitution("Colegio Matrículas");
        institution.setVatEnabled(true);
        institution.setVatRate(new BigDecimal("15.00"));
        institution.setVatEffectiveFrom(LocalDate.of(2026, 1, 1));
        institutionRepository.save(institution);
        newInstitutionAdmin(institution, "admin.matriculas@test.local", PASSWORD);
        token = login("admin.matriculas@test.local", PASSWORD);

        studentId = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");
        levelId = createLevel(token, "Básica Media");
        periodId = createPeriod(token, "2026-2027");
        serviceId = createService(token, "Transporte");
        planId = createPlan(token, "Plan Base", serviceId);
        createRate(token, serviceId, "20.00");
    }

    @Test
    void creaMatriculaConPeriodoLectivo() throws Exception {
        mockMvc.perform(post("/api/v1/enrollments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "studentId":"%s","educationalLevelId":"%s","planId":"%s",
                          "academicPeriodId":"%s","startDate":"2026-03-01","agreedPrice":100.00,
                          "services":[{"serviceId":"%s","agreedPrice":20.00,"quantity":1}]
                        }
                        """.formatted(studentId, levelId, planId, periodId, serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.academicPeriodId").value(periodId))
                .andExpect(jsonPath("$.studentName").value("Pérez, Ana"))
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.subtotal").value(120.00));
    }

    @Test
    void creaMatriculaSinPeriodoLectivo() throws Exception {
        String enrollmentId = createEnrollment(token, studentId, levelId, planId, null, "100.00", null, null);

        mockMvc.perform(get("/api/v1/enrollments/" + enrollmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicPeriodId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void confirmaMatriculaYCongelaElIva() throws Exception {
        String enrollmentId = createEnrollment(token, studentId, levelId, planId, periodId, "100.00", serviceId,
                "20.00");

        mockMvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.vatRate").value(15.00))
                .andExpect(jsonPath("$.vatAmount").value(18.00))
                .andExpect(jsonPath("$.totalAmount").value(138.00));

        mockMvc.perform(get("/api/v1/enrollments/" + enrollmentId + "/history")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[1].action").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].fromStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].toStatus").value("CONFIRMED"));
    }

    @Test
    void unaMatriculaConfirmadaNoSeEditaLibremente() throws Exception {
        String enrollmentId = createEnrollment(token, studentId, levelId, planId, null, "100.00", null, null);

        mockMvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/enrollments/" + enrollmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","educationalLevelId":"%s","startDate":"2026-03-01",
                         "agreedPrice":999.00}
                        """.formatted(studentId, levelId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void cancelaMatriculaConMotivo() throws Exception {
        String enrollmentId = createEnrollment(token, studentId, levelId, planId, null, "100.00", null, null);

        mockMvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"El estudiante se mudó de ciudad"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("El estudiante se mudó de ciudad"));

        mockMvc.perform(get("/api/v1/enrollments/" + enrollmentId + "/history")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].action").value("CANCELLED"))
                .andExpect(jsonPath("$[1].reason").value("El estudiante se mudó de ciudad"));
    }

    @Test
    void mantieneElPrecioAcordadoAunqueCambieLaTarifa() throws Exception {
        String rateId = createRate(token, serviceId, "20.00");
        String enrollmentId = createEnrollment(token, studentId, levelId, planId, null, "100.00", serviceId,
                "20.00");

        mockMvc.perform(put("/api/v1/catalog/rates/" + rateId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serviceId":"%s","amount":75.00,"validFrom":"2026-01-01"}
                        """.formatted(serviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(75.00));

        MvcResult result = mockMvc.perform(get("/api/v1/enrollments/" + enrollmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(readDecimal(result, "$.agreedPrice")).isEqualByComparingTo("100.00");
        assertThat(readDecimal(result, "$.services[0].agreedPrice")).isEqualByComparingTo("20.00");
        assertThat(readDecimal(result, "$.totalAmount")).isEqualByComparingTo("120.00");
    }

    @Test
    void noEliminaUnEstudianteConMatriculas() throws Exception {
        createEnrollment(token, studentId, levelId, planId, null, "100.00", null, null);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/students/" + studentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }
}
