package com.akira.educacion.finance;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.FinanceTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que los permisos financieros separados en la Fase 5B se apliquen en
 * el backend, aunque el frontend oculte los botones.
 */
class FinancialPermissionIntegrationTest extends FinanceTestSupport {

    private static final String PASSWORD = "Secreta123!";

    private Institution institution;

    @BeforeEach
    void setUp() {
        institution = newInstitution("Institución financiera");
        newInstitutionAdmin(institution, "admin@test.local", PASSWORD);
        newUser(institution, "director@test.local", PASSWORD, RoleCodes.DIRECTOR);
        newUser(institution, "tesoreria@test.local", PASSWORD, RoleCodes.TREASURY);
        newUser(institution, "secretaria@test.local", PASSWORD, RoleCodes.SECRETARY);
    }

    @Test
    void directorNoPuedeCrearEgresos() throws Exception {
        String token = login("director@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/finance/expenses")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/finance/expenses")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void secretariaNoPuedeConfirmarNiAnularEgresos() throws Exception {
        String admin = login("admin@test.local", PASSWORD);
        String categoryId = expenseId(createExpenseCategory(admin, "CAT-PERM", "Categoría permisos"));
        String expenseId = expenseId(createExpense(admin, categoryId, "25.00", LocalDate.now(), "CASH", "Prueba"));
        String token = login("secretaria@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/finance/expenses/" + expenseId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/finance/expenses/" + expenseId + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"prueba\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tesoreriaPuedeCrearConfirmarYAnularEgresos() throws Exception {
        String token = login("tesoreria@test.local", PASSWORD);
        String categoryId = expenseId(createExpenseCategory(token, "CAT-TES", "Categoría tesorería"));
        String expenseId = expenseId(createExpense(token, categoryId, "30.00", LocalDate.now(), "CASH",
                "Egreso de tesorería"));

        confirmExpense(token, expenseId);
        voidExpense(token, expenseId, "Anulación autorizada");
    }

    @Test
    void directorNoPuedeRevertirAplicacionesDePago() throws Exception {
        String token = login("director@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/billing/payments/" + UUID.randomUUID()
                + "/allocations/" + UUID.randomUUID() + "/reverse")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"prueba\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tesoreriaTienePermisoDeReversionPeroPagoInexistenteDevuelve404() throws Exception {
        String token = login("tesoreria@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/billing/payments/" + UUID.randomUUID()
                + "/allocations/" + UUID.randomUUID() + "/reverse")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"prueba\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void secretariaNoPuedeRegistrarPagos() throws Exception {
        String token = login("secretaria@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/billing/payments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "studentId":"%s",
                          "amount":10.00,
                          "method":"CASH",
                          "receivedDate":"%s"
                        }
                        """.formatted(UUID.randomUUID(), LocalDate.now())))
                .andExpect(status().isForbidden());
    }

    private String expenseBody(String categoryId) {
        return """
                {
                  "categoryId":"%s",
                  "amount":10.00,
                  "expenseDate":"%s",
                  "description":"Prueba de permisos",
                  "method":"CASH",
                  "beneficiary":"Proveedor",
                  "reference":null
                }
                """.formatted(categoryId, LocalDate.now());
    }
}
