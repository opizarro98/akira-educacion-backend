package com.akira.educacion.billing;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.BillingTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChargeIntegrationTest extends BillingTestSupport {

    private static final String PASSWORD = "Secreta123!";
    private static final String ISSUE_DATE = "2026-05-01";
    private static final String DUE_DATE = "2026-06-01";

    private Institution institution;
    private String token;
    private String treasuryToken;
    private String directorToken;
    private String secretaryToken;
    private String studentId;
    private String levelId;
    private String serviceId;
    private String conceptId;

    @BeforeEach
    void setUp() throws Exception {
        institution = newInstitution("Colegio Cobros");
        institution.setVatEnabled(true);
        institution.setVatRate(new BigDecimal("15.00"));
        institution.setVatEffectiveFrom(LocalDate.of(2026, 1, 1));
        institutionRepository.save(institution);

        newInstitutionAdmin(institution, "admin.cobros@test.local", PASSWORD);
        newUser(institution, "tesoreria.cobros@test.local", PASSWORD, RoleCodes.TREASURY);
        newUser(institution, "direccion.cobros@test.local", PASSWORD, RoleCodes.DIRECTOR);
        newUser(institution, "secretaria.cobros@test.local", PASSWORD, RoleCodes.SECRETARY);

        token = login("admin.cobros@test.local", PASSWORD);
        treasuryToken = login("tesoreria.cobros@test.local", PASSWORD);
        directorToken = login("direccion.cobros@test.local", PASSWORD);
        secretaryToken = login("secretaria.cobros@test.local", PASSWORD);

        studentId = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");
        levelId = createLevel(token, "Básica Media");
        serviceId = createService(token, "Transporte");
        createRate(token, serviceId, "20.00");
        conceptId = createConcept(token, "PENSION", "Pensión mensual", "50.00");
    }

    @Test
    void creaObligacionManualConIvaYCalculaTotales() throws Exception {
        MvcResult result = createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, "manual-1",
                manualItem(conceptId, "Pensión", 2, "100.00", "10.00"));

        mockMvc.perform(get("/api/v1/billing/charges/" + chargeId(result))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.subtotal").value(190.00))
                .andExpect(jsonPath("$.taxAmount").value(28.50))
                .andExpect(jsonPath("$.total").value(218.50))
                .andExpect(jsonPath("$.paidAmount").value(0.00))
                .andExpect(jsonPath("$.balance").value(218.50))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].taxableBase").value(190.00))
                .andExpect(jsonPath("$.items[0].vatRate").value(15.00))
                .andExpect(jsonPath("$.items[0].vatAmount").value(28.50))
                .andExpect(jsonPath("$.items[0].total").value(218.50))
                .andExpect(jsonPath("$.responsibleEmail").value("admin.cobros@test.local"));
    }

    @Test
    void generaCobroInicialDesdeMatriculaConfirmada() throws Exception {
        String enrollmentId = confirmedEnrollment("100.00", "20.00");

        MvcResult result = mockMvc.perform(post("/api/v1/billing/charges/from-enrollment/" + enrollmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("ENROLLMENT"))
                .andExpect(jsonPath("$.enrollmentId").value(enrollmentId))
                .andExpect(jsonPath("$.subtotal").value(120.00))
                .andExpect(jsonPath("$.taxAmount").value(18.00))
                .andExpect(jsonPath("$.total").value(138.00))
                .andExpect(jsonPath("$.balance").value(138.00))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].conceptCode").value("MATRICULA"))
                .andExpect(jsonPath("$.items[0].vatRate").value(15.00))
                .andExpect(jsonPath("$.items[1].conceptCode").value("SERVICIO"))
                .andExpect(jsonPath("$.items[1].unitPrice").value(20.00))
                .andReturn();

        mockMvc.perform(get("/api/v1/billing/charges/" + chargeId(result) + "/history")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("CREATED"));
    }

    @Test
    void aplicaIvaDesactivado() throws Exception {
        mockMvc.perform(put("/api/v1/institutions/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vatEnabled":false}
                        """))
                .andExpect(status().isOk());

        MvcResult result = createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, null,
                manualItem(conceptId, "Pensión", 1, "100.00", "0"));

        mockMvc.perform(get("/api/v1/billing/charges/" + chargeId(result))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxAmount").value(0.00))
                .andExpect(jsonPath("$.total").value(100.00))
                .andExpect(jsonPath("$.items[0].vatRate").value(0.00));
    }

    @Test
    void conservaLaTasaHistoricaAunqueCambieLaConfiguracionInstitucional() throws Exception {
        MvcResult result = createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, null,
                manualItem(conceptId, "Pensión", 1, "100.00", "0"));
        String id = chargeId(result);

        mockMvc.perform(put("/api/v1/institutions/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vatRate":5.00}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vatRate").value(5.00));

        mockMvc.perform(get("/api/v1/billing/charges/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxAmount").value(15.00))
                .andExpect(jsonPath("$.total").value(115.00))
                .andExpect(jsonPath("$.items[0].vatRate").value(15.00));

        MvcResult later = createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, null,
                manualItem(conceptId, "Pensión nueva", 1, "100.00", "0"));
        mockMvc.perform(get("/api/v1/billing/charges/" + chargeId(later))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxAmount").value(5.00));
    }

    @Test
    void conservaElPrecioAcordadoAunqueCambieLaTarifa() throws Exception {
        String enrollmentId = confirmedEnrollment("100.00", "20.00");
        createRate(token, serviceId, "75.00");

        MvcResult result = mockMvc.perform(post("/api/v1/billing/charges/from-enrollment/" + enrollmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.items[1].unitPrice").value(20.00))
                .andExpect(jsonPath("$.total").value(138.00))
                .andReturn();

        assertThat(readDecimal(result, "$.subtotal")).isEqualByComparingTo("120.00");
    }

    @Test
    void rechazaImportesNegativosYDescuentosInvalidos() throws Exception {
        mockMvc.perform(post("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","issueDate":"%s","dueDate":"%s","items":[
                          {"conceptId":"%s","quantity":1,"unitPrice":-5.00}
                        ]}
                        """.formatted(studentId, ISSUE_DATE, DUE_DATE, conceptId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","issueDate":"%s","dueDate":"%s","items":[
                          {"conceptId":"%s","quantity":1,"unitPrice":10.00,"discount":20.00}
                        ]}
                        """.formatted(studentId, ISSUE_DATE, DUE_DATE, conceptId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void rechazaReferenciasCruzadasEntreInstituciones() throws Exception {
        Institution other = newInstitution("Colegio Ajeno");
        newInstitutionAdmin(other, "admin.ajeno@test.local", PASSWORD);
        String otherToken = login("admin.ajeno@test.local", PASSWORD);
        String otherStudent = createStudent(otherToken, "Luis", "Ajeno", "1799999999", "ACTIVE");
        String otherConcept = createConcept(otherToken, "OTRO", "Concepto ajeno", "10.00");

        mockMvc.perform(post("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","issueDate":"%s","dueDate":"%s","items":[
                          {"conceptId":"%s","quantity":1,"unitPrice":10.00}
                        ]}
                        """.formatted(otherStudent, ISSUE_DATE, DUE_DATE, conceptId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","issueDate":"%s","dueDate":"%s","items":[
                          {"conceptId":"%s","quantity":1,"unitPrice":10.00}
                        ]}
                        """.formatted(studentId, ISSUE_DATE, DUE_DATE, otherConcept)))
                .andExpect(status().isNotFound());
    }

    @Test
    void evitaDuplicadosAlReintentarLaGeneracion() throws Exception {
        String enrollmentId = confirmedEnrollment("100.00", null);

        MvcResult first = mockMvc.perform(post("/api/v1/billing/charges/from-enrollment/" + enrollmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/billing/charges/from-enrollment/" + enrollmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(chargeId(second)).isEqualTo(chargeId(first));
        Integer charges = jdbcTemplate.queryForObject(
                "select count(*) from charge where institution_id = ? and origin = 'ENROLLMENT'", Integer.class,
                institution.getId());
        assertThat(charges).isEqualTo(1);

        MvcResult manual = createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, "clave-repetida",
                manualItem(conceptId, "Pensión", 1, "10.00", "0"));
        MvcResult manualRetry = createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE,
                "clave-repetida", manualItem(conceptId, "Pensión", 1, "999.00", "0"));
        assertThat(chargeId(manualRetry)).isEqualTo(chargeId(manual));
    }

    @Test
    void consultaObligacionesConFiltrosPorEstadoEstudianteYFechas() throws Exception {
        String charge = chargeId(createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, null,
                manualItem(conceptId, "Pensión", 1, "100.00", "0")));

        mockMvc.perform(get("/api/v1/billing/charges")
                .param("status", "PENDING")
                .param("studentId", studentId)
                .param("issueFrom", "2026-01-01")
                .param("issueTo", "2026-12-31")
                .param("dueFrom", "2026-01-01")
                .param("dueTo", "2026-12-31")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(charge))
                .andExpect(jsonPath("$.content[0].studentName").value("Pérez, Ana"));

        mockMvc.perform(get("/api/v1/billing/charges")
                .param("status", "VOID")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/billing/charges")
                .param("issueFrom", "2027-01-01")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void impideAccesoAOblicacionesDeOtraInstitucion() throws Exception {
        Institution other = newInstitution("Colegio Ajeno 2");
        newInstitutionAdmin(other, "admin.ajeno2@test.local", PASSWORD);
        String otherToken = login("admin.ajeno2@test.local", PASSWORD);
        String otherStudent = createStudent(otherToken, "Luis", "Ajeno", "1799999991", "ACTIVE");
        String otherConcept = createConcept(otherToken, "OTRO2", "Concepto ajeno", "10.00");
        String otherCharge = chargeId(createManualCharge(otherToken, otherStudent, otherConcept, ISSUE_DATE,
                DUE_DATE, null, manualItem(otherConcept, "Ajeno", 1, "10.00", "0")));

        mockMvc.perform(get("/api/v1/billing/charges/" + otherCharge)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/billing/charges")
                .param("institutionId", other.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void verificaPermisosPorRol() throws Exception {
        mockMvc.perform(post("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","issueDate":"%s","dueDate":"%s","items":[
                          {"conceptId":"%s","quantity":1,"unitPrice":10.00}
                        ]}
                        """.formatted(studentId, ISSUE_DATE, DUE_DATE, conceptId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","issueDate":"%s","dueDate":"%s","items":[
                          {"conceptId":"%s","quantity":1,"unitPrice":10.00}
                        ]}
                        """.formatted(studentId, ISSUE_DATE, DUE_DATE, conceptId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());
    }

    @Test
    void emitirUnaObligacionNoRegistraPagos() throws Exception {
        MvcResult result = createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, null,
                manualItem(conceptId, "Pensión", 1, "100.00", "0"));

        mockMvc.perform(get("/api/v1/billing/charges/" + chargeId(result) + "/balance")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(0.00))
                .andExpect(jsonPath("$.balance").value(115.00));

        Integer payments = jdbcTemplate.queryForObject(
                "select count(*) from payment where institution_id = ?", Integer.class, institution.getId());
        assertThat(payments).isZero();

        Integer applications = jdbcTemplate.queryForObject(
                "select count(*) from payment_allocation where charge_id = ?", Integer.class,
                UUID.fromString(chargeId(result)));
        assertThat(applications).isZero();
    }

    @Test
    void anulaObligacionConMotivoYDejaHistorial() throws Exception {
        String charge = chargeId(createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, null,
                manualItem(conceptId, "Pensión", 1, "100.00", "0")));

        mockMvc.perform(post("/api/v1/billing/charges/" + charge + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"Error en el importe"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.voidReason").value("Error en el importe"));

        mockMvc.perform(get("/api/v1/billing/charges/" + charge + "/history")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].action").value("VOIDED"))
                .andExpect(jsonPath("$[1].reason").value("Error en el importe"));

        mockMvc.perform(post("/api/v1/billing/charges/" + charge + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"Otra vez"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void generaObligacionMensualAsistidaDeFormaIdempotente() throws Exception {
        mockMvc.perform(put("/api/v1/billing/settings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"frequency":"MONTHLY","issueDay":5,"dueDays":10}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequency").value("MONTHLY"));

        String body = """
                {"studentId":"%s","conceptId":"%s","period":"2026-11"}
                """.formatted(studentId, conceptId);

        MvcResult first = mockMvc.perform(post("/api/v1/billing/charges/generate-monthly")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("MONTHLY"))
                .andExpect(jsonPath("$.issueDate").value("2026-11-05"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.total").value(57.50))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/billing/charges/generate-monthly")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(chargeId(second)).isEqualTo(chargeId(first));
    }

    private String confirmedEnrollment(String agreedPrice, String servicePrice) throws Exception {
        String enrollmentId = createEnrollment(token, studentId, levelId, null, null, agreedPrice,
                servicePrice == null ? null : serviceId, servicePrice);
        mockMvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        return enrollmentId;
    }

    @SuppressWarnings("unused")
    private static UUID asUuid(String value) {
        return UUID.fromString(value);
    }
}
