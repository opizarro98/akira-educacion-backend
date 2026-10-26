package com.akira.educacion.finance;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.FinanceTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de la Fase 4: egresos, movimientos de caja, reportes
 * y dashboard. Todos los datos se crean por la API real.
 */
class FinanceIntegrationTest extends FinanceTestSupport {

    private static final String PASSWORD = "Secreta123!";

    private final LocalDate today = LocalDate.now();
    private final String from = today.withDayOfMonth(1).toString();
    private final String to = today.toString();

    private Institution institution;
    private String token;
    private String treasuryToken;
    private String secretaryToken;
    private String directorToken;
    private String studentId;
    private String conceptId;
    private String categoryId;

    @BeforeEach
    void setUp() throws Exception {
        institution = newInstitution("Colegio Finanzas");

        newInstitutionAdmin(institution, "admin.finanzas@test.local", PASSWORD);
        newUser(institution, "tesoreria.finanzas@test.local", PASSWORD, RoleCodes.TREASURY);
        newUser(institution, "secretaria.finanzas@test.local", PASSWORD, RoleCodes.SECRETARY);
        newUser(institution, "direccion.finanzas@test.local", PASSWORD, RoleCodes.DIRECTOR);

        token = login("admin.finanzas@test.local", PASSWORD);
        treasuryToken = login("tesoreria.finanzas@test.local", PASSWORD);
        secretaryToken = login("secretaria.finanzas@test.local", PASSWORD);
        directorToken = login("direccion.finanzas@test.local", PASSWORD);

        studentId = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");
        conceptId = createConcept(token, "PENSION", "Pensión mensual", "100.00");
        categoryId = expenseId(createExpenseCategory(treasuryToken, "SERV", "Servicios básicos"));
    }

    @Test
    void creaCategoriaDeEgresoYRechazaDuplicados() throws Exception {
        assertThat(categoryId).isNotBlank();

        mockMvc.perform(get("/api/v1/finance/expense-categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Servicios básicos"));

        mockMvc.perform(post("/api/v1/finance/expense-categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"SERV2","name":"Servicios básicos","active":true}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void registraEgresoEnBorradorYPermiteEditarlo() throws Exception {
        String expense = expenseId(createExpense(treasuryToken, categoryId, "35.50", today, "CASH",
                "Material de limpieza"));

        mockMvc.perform(get("/api/v1/finance/expenses/" + expense)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.amount").value(35.50))
                .andExpect(jsonPath("$.categoryName").value("Servicios básicos"))
                .andExpect(jsonPath("$.beneficiary").value("Proveedor de prueba"));

        mockMvc.perform(put("/api/v1/finance/expenses/" + expense)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "categoryId":"%s",
                          "amount":40.00,
                          "expenseDate":"%s",
                          "description":"Material de limpieza corregido",
                          "method":"BANK_TRANSFER",
                          "beneficiary":"Proveedor de prueba",
                          "reference":"FAC-002"
                        }
                        """.formatted(categoryId, today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"));
    }

    @Test
    void confirmaEgresoYBloqueaLaEdicion() throws Exception {
        String expense = expenseId(createExpense(treasuryToken, categoryId, "60.00", today, "CASH", "Mantenimiento"));

        mockMvc.perform(post("/api/v1/finance/expenses/" + expense + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.confirmedByEmail").value("tesoreria.finanzas@test.local"));

        mockMvc.perform(put("/api/v1/finance/expenses/" + expense)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "categoryId":"%s",
                          "amount":99.00,
                          "expenseDate":"%s",
                          "description":"Intento de edición",
                          "method":"CASH"
                        }
                        """.formatted(categoryId, today)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/finance/expenses/" + expense + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/finance/expenses/" + expense + "/history")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("REGISTERED"))
                .andExpect(jsonPath("$[1].action").value("CONFIRMED"));
    }

    @Test
    void confirmarEgresoGeneraSalidaDeCaja() throws Exception {
        String expense = expenseId(createExpense(treasuryToken, categoryId, "60.00", today, "CASH", "Mantenimiento"));
        confirmExpense(treasuryToken, expense);

        mockMvc.perform(get("/api/v1/finance/cash-movements")
                .param("type", "EXPENSE_OUT")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(60.00))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"))
                .andExpect(jsonPath("$.content[0].sourceType").value("EXPENSE"))
                .andExpect(jsonPath("$.content[0].sourceId").value(expense));
    }

    @Test
    void confirmarPagoGeneraEntradaDeCaja() throws Exception {
        String payment = paymentId(registerPaymentOn(treasuryToken, studentId, "75.00", "BANK_TRANSFER",
                "TRF-100", "pago-caja-1", today));

        mockMvc.perform(get("/api/v1/finance/cash-movements")
                .param("type", "PAYMENT_IN")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        confirmPayment(treasuryToken, payment);

        mockMvc.perform(get("/api/v1/finance/cash-movements")
                .param("type", "PAYMENT_IN")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(75.00))
                .andExpect(jsonPath("$.content[0].direction").value("IN"))
                .andExpect(jsonPath("$.content[0].sourceType").value("PAYMENT"))
                .andExpect(jsonPath("$.content[0].sourceId").value(payment));
    }

    @Test
    void pagoPendienteNoGeneraEntradaDeCaja() throws Exception {
        registerPaymentOn(treasuryToken, studentId, "75.00", "BANK_TRANSFER", "TRF-101", "pago-caja-2", today);

        mockMvc.perform(get("/api/v1/finance/cash-movements")
                .param("type", "PAYMENT_IN")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.paymentCount").value(0));

        mockMvc.perform(get("/api/v1/finance/reports/pending-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value(75.00));
    }

    @Test
    void anularPagoGeneraReversionYRestauraElSaldo() throws Exception {
        String charge = createCharge("cargo-rev-1", today, today.plusDays(20));
        String payment = paymentId(registerPaymentOn(treasuryToken, studentId, "50.00", "CASH", null,
                "pago-rev-1", today));

        mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idempotencyKey":"op-rev-1","allocations":[{"chargeId":"%s","amount":40.00}]}
                        """.formatted(charge)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"Error de caja"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.appliedAmount").value(0.00))
                .andExpect(jsonPath("$.allocations[0].reversed").value(true));

        mockMvc.perform(get("/api/v1/billing/charges/" + charge)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.paidAmount").value(0.00))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/finance/cash-movements")
                .param("type", "PAYMENT_VOID_REVERSAL")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(50.00))
                .andExpect(jsonPath("$.content[0].direction").value("OUT"));
    }

    @Test
    void anularEgresoGeneraReversionDeCaja() throws Exception {
        String expense = expenseId(createExpense(treasuryToken, categoryId, "30.00", today, "CASH", "Varios"));
        confirmExpense(treasuryToken, expense);

        mockMvc.perform(post("/api/v1/finance/expenses/" + expense + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"Egreso duplicado"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.voidReason").value("Egreso duplicado"));

        mockMvc.perform(get("/api/v1/finance/cash-movements")
                .param("type", "EXPENSE_VOID_REVERSAL")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(30.00))
                .andExpect(jsonPath("$.content[0].direction").value("IN"));

        mockMvc.perform(get("/api/v1/finance/reports/cash-summary")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inflows").value(30.00))
                .andExpect(jsonPath("$.outflows").value(30.00))
                .andExpect(jsonPath("$.net").value(0.00));
    }

    @Test
    void evitaMovimientosDeCajaDuplicados() throws Exception {
        String expense = expenseId(createExpense(treasuryToken, categoryId, "20.00", today, "CASH", "Suministros"));
        confirmExpense(treasuryToken, expense);
        mockMvc.perform(post("/api/v1/finance/expenses/" + expense + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isConflict());

        String payment = paymentId(registerPaymentOn(treasuryToken, studentId, "25.00", "CASH", null,
                "pago-dup-1", today));
        mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isConflict());

        Integer expenseMovements = jdbcTemplate.queryForObject(
                "select count(*) from cash_movement where expense_id = ? and movement_type = 'EXPENSE_OUT'",
                Integer.class, java.util.UUID.fromString(expense));
        Integer paymentMovements = jdbcTemplate.queryForObject(
                "select count(*) from cash_movement where payment_id = ? and movement_type = 'PAYMENT_IN'",
                Integer.class, java.util.UUID.fromString(payment));
        assertThat(expenseMovements).isEqualTo(1);
        assertThat(paymentMovements).isEqualTo(1);
    }

    @Test
    void separaObligacionesGeneradasDeDineroRecibido() throws Exception {
        createCharge("cargo-sep-1", today, today.plusDays(20));

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.accrued").value(100.00))
                .andExpect(jsonPath("$.header.collected").value(0.00))
                .andExpect(jsonPath("$.header.difference").value(100.00))
                .andExpect(jsonPath("$.totalAmount").value(0.00));

        mockMvc.perform(get("/api/v1/finance/dashboard")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingTotal").value(100.00))
                .andExpect(jsonPath("$.confirmedPaymentsAmount").value(0.00))
                .andExpect(jsonPath("$.cashBalance").value(0.00));
    }

    @Test
    void filtraReportesPorInstitucion() throws Exception {
        registerPaymentOn(treasuryToken, studentId, "50.00", "CASH", null, "pago-filtro-1", today);

        Institution other = newInstitution("Colegio Ajeno Finanzas");
        newInstitutionAdmin(other, "admin.ajeno.finanzas@test.local", PASSWORD);
        String otherToken = login("admin.ajeno.finanzas@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(50.00))
                .andExpect(jsonPath("$.header.institutionId").value(institution.getId().toString()));

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0.00));

        newPlatformAdmin("plataforma.finanzas@test.local", PASSWORD);
        String platformToken = login("plataforma.finanzas@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("institutionId", institution.getId().toString())
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(50.00));

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaAccesoCruzadoEntreInstituciones() throws Exception {
        Institution other = newInstitution("Colegio Cruzado Finanzas");
        newInstitutionAdmin(other, "admin.cruzado.finanzas@test.local", PASSWORD);
        String otherToken = login("admin.cruzado.finanzas@test.local", PASSWORD);
        String otherCategory = expenseId(createExpenseCategory(otherToken, "OTRO", "Categoría ajena"));
        String otherExpense = expenseId(createExpense(otherToken, otherCategory, "15.00", today, "CASH", "Ajeno"));

        mockMvc.perform(get("/api/v1/finance/expenses/" + otherExpense)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/finance/expenses")
                .param("institutionId", other.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("institutionId", other.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/finance/expenses")
                .param("institutionId", other.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId":"%s","amount":10.00,"expenseDate":"%s","description":"Cruce","method":"CASH"}
                        """.formatted(otherCategory, today)))
                .andExpect(status().isForbidden());

        // La FK compuesta de cash_movement impide incluso un INSERT directo
        // que apunte a un egreso de otra institución.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into cash_movement (id, institution_id, movement_type, source_type, expense_id,
                    amount, currency, occurred_at, created_at, updated_at, version)
                values (?, ?, 'EXPENSE_OUT', 'EXPENSE', ?, 1.00, 'USD', now(), now(), now(), 0)
                """, java.util.UUID.randomUUID(), institution.getId(), java.util.UUID.fromString(otherExpense)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consultaCarteraVencidaCorrectamente() throws Exception {
        createCharge("cargo-vencido-1", today.minusDays(40), today.minusDays(10));
        createCharge("cargo-vigente-1", today, today.plusDays(10));

        mockMvc.perform(get("/api/v1/finance/reports/outstanding")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(200.00))
                .andExpect(jsonPath("$.overdueOutstanding").value(100.00))
                .andExpect(jsonPath("$.outstandingCount").value(2))
                .andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.rows[0].overdue").value(true))
                .andExpect(jsonPath("$.rows[0].daysOverdue").value(10))
                .andExpect(jsonPath("$.rows[0].balance").value(100.00));
    }

    @Test
    void conciliaDashboardConOperacionesConfirmadas() throws Exception {
        createCharge("cargo-dash-1", today, today.plusDays(20));
        registerPaymentOn(treasuryToken, studentId, "100.00", "CASH", null, "pago-dash-1", today);
        String expense = expenseId(createExpense(treasuryToken, categoryId, "40.00", today, "CASH", "Servicios"));
        confirmExpense(treasuryToken, expense);

        mockMvc.perform(get("/api/v1/finance/dashboard")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStudents").value(1))
                .andExpect(jsonPath("$.confirmedEnrollments").value(0))
                .andExpect(jsonPath("$.outstandingCharges").value(1))
                .andExpect(jsonPath("$.outstandingTotal").value(100.00))
                .andExpect(jsonPath("$.overdueTotal").value(0.00))
                .andExpect(jsonPath("$.confirmedPaymentsAmount").value(100.00))
                .andExpect(jsonPath("$.confirmedPaymentsCount").value(1))
                .andExpect(jsonPath("$.expensesAmount").value(40.00))
                .andExpect(jsonPath("$.expensesCount").value(1))
                .andExpect(jsonPath("$.cashInflows").value(100.00))
                .andExpect(jsonPath("$.cashOutflows").value(40.00))
                .andExpect(jsonPath("$.cashBalance").value(60.00))
                .andExpect(jsonPath("$.paymentsByMethod[0].method").value("CASH"))
                .andExpect(jsonPath("$.paymentsByMethod[0].amount").value(100.00))
                .andExpect(jsonPath("$.expensesByCategory[0].categoryName").value("Servicios básicos"))
                .andExpect(jsonPath("$.expensesByCategory[0].amount").value(40.00));
    }

    @Test
    void verificaReportesDespuesDeAnulaciones() throws Exception {
        String payment = paymentId(registerPaymentOn(treasuryToken, studentId, "100.00", "CASH", null,
                "pago-anul-1", today));
        String expense = expenseId(createExpense(treasuryToken, categoryId, "50.00", today, "CASH", "Servicios"));
        confirmExpense(treasuryToken, expense);

        mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"Pago duplicado"}
                        """))
                .andExpect(status().isOk());
        voidExpense(treasuryToken, expense, "Egreso duplicado");

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0.00))
                .andExpect(jsonPath("$.paymentCount").value(0));

        mockMvc.perform(get("/api/v1/finance/reports/voided-payments")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value(100.00));

        mockMvc.perform(get("/api/v1/finance/reports/expenses")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0.00))
                .andExpect(jsonPath("$.expenseCount").value(0));

        mockMvc.perform(get("/api/v1/finance/reports/cash-flow")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inflows").value(150.00))
                .andExpect(jsonPath("$.outflows").value(150.00))
                .andExpect(jsonPath("$.net").value(0.00))
                .andExpect(jsonPath("$.movementCount").value(4));
    }

    @Test
    void mantieneImportesConBigDecimalYNumeroSql() throws Exception {
        String first = expenseId(createExpense(treasuryToken, categoryId, "0.10", today, "CASH", "Centavos 1"));
        String second = expenseId(createExpense(treasuryToken, categoryId, "0.20", today, "CASH", "Centavos 2"));
        confirmExpense(treasuryToken, first);
        confirmExpense(treasuryToken, second);

        MvcResult report = mockMvc.perform(get("/api/v1/finance/reports/expenses")
                .param("from", from)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal total = readDecimal(report, "$.totalAmount");
        assertThat(total).isEqualByComparingTo("0.30");

        BigDecimal sqlTotal = jdbcTemplate.queryForObject(
                "select sum(amount) from expense where institution_id = ?",
                BigDecimal.class, institution.getId());
        assertThat(sqlTotal).isEqualByComparingTo("0.30");
        assertThat(sqlTotal.toPlainString()).isEqualTo("0.30");
    }

    @Test
    void respetaZonaHorariaYLimitesDeFechas() throws Exception {
        String expense = expenseId(createExpense(treasuryToken, categoryId, "10.00", today, "CASH", "Hoy"));
        confirmExpense(treasuryToken, expense);

        mockMvc.perform(get("/api/v1/finance/reports/cash-flow")
                .param("from", to)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementCount").value(1))
                .andExpect(jsonPath("$.header.timezone").value("America/Guayaquil"))
                .andExpect(jsonPath("$.header.fromDate").value(to))
                .andExpect(jsonPath("$.header.toDate").value(to));

        String tomorrow = today.plusDays(1).toString();
        mockMvc.perform(get("/api/v1/finance/reports/cash-flow")
                .param("from", tomorrow)
                .param("to", tomorrow)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementCount").value(0));

        mockMvc.perform(get("/api/v1/finance/reports/cash-flow")
                .param("from", tomorrow)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/finance/reports/expenses")
                .param("from", to)
                .param("to", to)
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseCount").value(1));
    }

    @Test
    void verificaPermisosPorRol() throws Exception {
        mockMvc.perform(post("/api/v1/finance/expense-categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Sin permiso"}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/finance/expenses")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                                {"categoryId":"%s","amount":10.00,"expenseDate":"%s","description":"Sin permiso","method":"CASH"}
                                """
                                .formatted(categoryId, today)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/finance/expenses")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/finance/reports/confirmed-payments")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk());

        String expense = expenseId(createExpense(treasuryToken, categoryId, "10.00", today, "CASH", "Permisos"));
        mockMvc.perform(post("/api/v1/finance/expenses/" + expense + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/finance/expenses/" + expense + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"Sin permiso"}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/finance/expenses/" + expense)
                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/finance/expenses/" + expense + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                .andExpect(status().isOk());
    }

    private String createCharge(String idempotencyKey, LocalDate issueDate, LocalDate dueDate) throws Exception {
        return chargeId(createManualCharge(treasuryToken, studentId, conceptId, issueDate.toString(),
                dueDate.toString(), idempotencyKey,
                manualItem(conceptId, "Pensión", 1, "100.00", "0")));
    }
}
