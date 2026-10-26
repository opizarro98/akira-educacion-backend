package com.akira.educacion.billing;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.PaymentTestSupport;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentIntegrationTest extends PaymentTestSupport {

        private static final String PASSWORD = "Secreta123!";
        private static final String ISSUE_DATE = "2026-10-01";
        private static final String DUE_DATE = "2026-10-31";

        private Institution institution;
        private String token;
        private String treasuryToken;
        private String directorToken;
        private String secretaryToken;
        private String studentId;
        private String conceptId;
        private String chargeOne;
        private String chargeTwo;

        @BeforeEach
        void setUp() throws Exception {
                institution = newInstitution("Colegio Pagos");
                institution.setVatEnabled(true);
                institution.setVatRate(new BigDecimal("15.00"));
                institution.setVatEffectiveFrom(LocalDate.of(2026, 1, 1));
                institutionRepository.save(institution);

                newInstitutionAdmin(institution, "admin.pagos@test.local", PASSWORD);
                newUser(institution, "tesoreria.pagos@test.local", PASSWORD, RoleCodes.TREASURY);
                newUser(institution, "direccion.pagos@test.local", PASSWORD, RoleCodes.DIRECTOR);
                newUser(institution, "secretaria.pagos@test.local", PASSWORD, RoleCodes.SECRETARY);

                token = login("admin.pagos@test.local", PASSWORD);
                treasuryToken = login("tesoreria.pagos@test.local", PASSWORD);
                directorToken = login("direccion.pagos@test.local", PASSWORD);
                secretaryToken = login("secretaria.pagos@test.local", PASSWORD);

                studentId = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");
                conceptId = createConcept(token, "PENSION", "Pensión mensual", "100.00");
                chargeOne = createCharge("cargo-1");
                chargeTwo = createCharge("cargo-2");
        }

        @Test
        void registraPagoEnEfectivoConfirmadoYEmiteRecibo() throws Exception {
                MvcResult result = registerPayment(treasuryToken, studentId, "150.00", "CASH", null, "pago-efectivo-1");

                mockMvc.perform(get("/api/v1/billing/payments/" + paymentId(result))
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                                .andExpect(jsonPath("$.amount").value(150.00))
                                .andExpect(jsonPath("$.appliedAmount").value(0.00))
                                .andExpect(jsonPath("$.availableAmount").value(150.00))
                                .andExpect(jsonPath("$.confirmedByEmail").value("tesoreria.pagos@test.local"))
                                .andExpect(jsonPath("$.receipt.receiptNumber").isNotEmpty())
                                .andExpect(jsonPath("$.receipt.amount").value(150.00))
                                .andExpect(jsonPath("$.receipt.voided").value(false));

                mockMvc.perform(get("/api/v1/billing/payments/" + paymentId(result) + "/history")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2))
                                .andExpect(jsonPath("$[0].action").value("REGISTERED"))
                                .andExpect(jsonPath("$[1].action").value("CONFIRMED"));
        }

        @Test
        void registraTransferenciaPendienteYNoCuentaComoDineroDisponible() throws Exception {
                MvcResult result = registerTransfer(treasuryToken, studentId, "150.00", "TRF-001", "transferencia-1");

                mockMvc.perform(get("/api/v1/billing/payments/" + paymentId(result))
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                                .andExpect(jsonPath("$.availableAmount").value(0.00))
                                .andExpect(jsonPath("$.receipt").doesNotExist());

                mockMvc.perform(post("/api/v1/billing/payments/" + paymentId(result) + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-pendiente", chargeOne, "50.00")))
                                .andExpect(status().isConflict());
        }

        @Test
        void confirmaTransferenciaYEmiteRecibo() throws Exception {
                MvcResult pending = registerTransfer(treasuryToken, studentId, "150.00", "TRF-002", "transferencia-2");

                mockMvc.perform(post("/api/v1/billing/payments/" + paymentId(pending) + "/confirm")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                                .andExpect(jsonPath("$.receipt.receiptNumber").isNotEmpty());

                mockMvc.perform(post("/api/v1/billing/payments/" + paymentId(pending) + "/confirm")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isConflict());
        }

        @Test
        void aplicaPagoParcialYActualizaElSaldoDeLaObligacion() throws Exception {
                String payment = paymentId(
                                registerPayment(treasuryToken, studentId, "50.00", "CASH", null, "parcial-1"));

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-parcial-1", chargeOne, "50.00")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.appliedAmount").value(50.00))
                                .andExpect(jsonPath("$.availableAmount").value(0.00))
                                .andExpect(jsonPath("$.allocations.length()").value(1));

                mockMvc.perform(get("/api/v1/billing/charges/" + chargeOne)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PARTIAL"))
                                .andExpect(jsonPath("$.paidAmount").value(50.00))
                                .andExpect(jsonPath("$.balance").value(65.00))
                                .andExpect(jsonPath("$.total").value(115.00));
        }

        @Test
        void aplicaPagoAVariasObligacionesEnUnaSolaOperacion() throws Exception {
                String payment = paymentId(
                                registerPayment(treasuryToken, studentId, "150.00", "CASH", null, "multiple-1"));

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "idempotencyKey":"op-multiple-1",
                                                  "allocations":[
                                                    {"chargeId":"%s","amount":115.00},
                                                    {"chargeId":"%s","amount":35.00}
                                                  ]
                                                }
                                                """.formatted(chargeOne, chargeTwo)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.appliedAmount").value(150.00))
                                .andExpect(jsonPath("$.allocations.length()").value(2));

                mockMvc.perform(get("/api/v1/billing/charges/" + chargeOne)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PAID"))
                                .andExpect(jsonPath("$.balance").value(0.00));

                mockMvc.perform(get("/api/v1/billing/charges/" + chargeTwo)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PARTIAL"))
                                .andExpect(jsonPath("$.balance").value(80.00));
        }

        @Test
        void rechazaAplicacionesQueSuperanElPagoOSuSaldo() throws Exception {
                String smallPayment = paymentId(
                                registerPayment(treasuryToken, studentId, "50.00", "CASH", null, "limite-1"));

                mockMvc.perform(post("/api/v1/billing/payments/" + smallPayment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-limite-1", chargeOne, "60.00")))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("CONFLICT"));

                String bigPayment = paymentId(
                                registerPayment(treasuryToken, studentId, "200.00", "CASH", null, "limite-2"));
                mockMvc.perform(post("/api/v1/billing/payments/" + bigPayment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-limite-2", chargeOne, "150.00")))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("CONFLICT"));

                mockMvc.perform(get("/api/v1/billing/charges/" + chargeOne)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.paidAmount").value(0.00))
                                .andExpect(jsonPath("$.balance").value(115.00));
        }

        @Test
        void evitaAplicacionesDuplicadasYReintentosIdempotentes() throws Exception {
                String payment = paymentId(
                                registerPayment(treasuryToken, studentId, "100.00", "CASH", null, "duplicado-1"));

                MvcResult first = mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-duplicado-1", chargeOne, "40.00")))
                                .andExpect(status().isOk())
                                .andReturn();

                MvcResult retry = mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-duplicado-1", chargeOne, "40.00")))
                                .andExpect(status().isOk())
                                .andReturn();

                assertThat(readDecimal(retry, "$.appliedAmount")).isEqualByComparingTo("40.00");
                Integer allocations = jdbcTemplate.queryForObject(
                                "select count(*) from payment_allocation where payment_id = ? and reversed = false",
                                Integer.class,
                                java.util.UUID.fromString(paymentId(first)));
                assertThat(allocations).isEqualTo(1);

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-duplicado-2", chargeOne, "10.00")))
                                .andExpect(status().isConflict());

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"idempotencyKey":"op-duplicado-3","allocations":[
                                                  {"chargeId":"%s","amount":10.00},
                                                  {"chargeId":"%s","amount":10.00}
                                                ]}
                                                """.formatted(chargeTwo, chargeTwo)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void rechazaReferenciasCruzadasEntreInstituciones() throws Exception {
                Institution other = newInstitution("Colegio Ajeno Pagos");
                newInstitutionAdmin(other, "admin.ajeno.pagos@test.local", PASSWORD);
                String otherToken = login("admin.ajeno.pagos@test.local", PASSWORD);
                String otherStudent = createStudent(otherToken, "Luis", "Ajeno", "1799999999", "ACTIVE");
                String otherConcept = createConcept(otherToken, "OTRO", "Concepto ajeno", "10.00");
                String otherCharge = chargeId(createManualCharge(otherToken, otherStudent, otherConcept, ISSUE_DATE,
                                DUE_DATE, null, manualItem(otherConcept, "Ajeno", 1, "10.00", "0")));
                String otherPayment = paymentId(registerPayment(otherToken, otherStudent, "50.00", "CASH", null, null));

                String payment = paymentId(registerPayment(treasuryToken, studentId, "50.00", "CASH", null, "cruce-1"));
                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-cruce-1", otherCharge, "10.00")))
                                .andExpect(status().isNotFound());

                mockMvc.perform(get("/api/v1/billing/payments/" + otherPayment)
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isNotFound());

                mockMvc.perform(get("/api/v1/billing/payments")
                                .param("institutionId", other.getId().toString())
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isForbidden());
        }

        @Test
        void rechazaAplicarUnPagoAnulado() throws Exception {
                String payment = paymentId(
                                registerPayment(treasuryToken, studentId, "50.00", "CASH", null, "anulado-1"));

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/void")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"reason":"Pago duplicado"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("VOID"));

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-anulado-1", chargeOne, "10.00")))
                                .andExpect(status().isConflict());
        }

        @Test
        void revierteAplicacionYRestauraElSaldo() throws Exception {
                String payment = paymentId(
                                registerPayment(treasuryToken, studentId, "100.00", "CASH", null, "reversion-1"));

                MvcResult applied = mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-reversion-1", chargeOne, "40.00")))
                                .andExpect(status().isOk())
                                .andReturn();
                String allocationId = com.jayway.jsonpath.JsonPath.read(
                                applied.getResponse().getContentAsString(), "$.allocations[0].id");

                mockMvc.perform(post(
                                "/api/v1/billing/payments/" + payment + "/allocations/" + allocationId + "/reverse")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"reason":"Aplicación registrada por error"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.appliedAmount").value(0.00))
                                .andExpect(jsonPath("$.allocations[0].reversed").value(true))
                                .andExpect(jsonPath("$.allocations[0].reversalReason")
                                                .value("Aplicación registrada por error"));

                mockMvc.perform(get("/api/v1/billing/charges/" + chargeOne)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.paidAmount").value(0.00))
                                .andExpect(jsonPath("$.balance").value(115.00));

                mockMvc.perform(post(
                                "/api/v1/billing/payments/" + payment + "/allocations/" + allocationId + "/reverse")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"reason":"Repetida"}
                                                """))
                                .andExpect(status().isConflict());
        }

        @Test
        void anulaPagoAutorizadoConservandoHistorialYAnulandoRecibo() throws Exception {
                String payment = paymentId(
                                registerPayment(treasuryToken, studentId, "100.00", "CASH", null, "anulacion-1"));

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/void")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"reason":"Error de caja"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("VOID"))
                                .andExpect(jsonPath("$.voidReason").value("Error de caja"))
                                .andExpect(jsonPath("$.receipt.voided").value(true))
                                .andExpect(jsonPath("$.receipt.voidReason").value("Error de caja"));

                mockMvc.perform(get("/api/v1/billing/payments/" + payment + "/history")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(3))
                                .andExpect(jsonPath("$[2].action").value("VOIDED"));

                String appliedPayment = paymentId(
                                registerPayment(treasuryToken, studentId, "50.00", "CASH", null, "anulacion-2"));
                mockMvc.perform(post("/api/v1/billing/payments/" + appliedPayment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-anulacion-2", chargeOne, "10.00")))
                                .andExpect(status().isOk());

                // Fase 4: la anulación revierte automáticamente las aplicaciones
                // vigentes en lugar de exigir una reversión manual previa.
                mockMvc.perform(post("/api/v1/billing/payments/" + appliedPayment + "/void")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"reason":"Anulación atómica del pago"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("VOID"))
                                .andExpect(jsonPath("$.appliedAmount").value(0.00))
                                .andExpect(jsonPath("$.allocations[0].reversed").value(true))
                                .andExpect(jsonPath("$.allocations[0].reversalReason")
                                                .value("Anulación atómica del pago"));

                mockMvc.perform(get("/api/v1/billing/charges/" + chargeOne)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.paidAmount").value(0.00))
                                .andExpect(jsonPath("$.balance").value(115.00));
        }

        @Test
        void verificaPermisosPorRol() throws Exception {
                mockMvc.perform(post("/api/v1/billing/payments")
                                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"studentId":"%s","amount":10.00,"method":"CASH"}
                                                """.formatted(studentId)))
                                .andExpect(status().isForbidden());

                String pending = paymentId(registerTransfer(treasuryToken, studentId, "20.00", "TRF-ROL", "rol-1"));
                mockMvc.perform(post("/api/v1/billing/payments/" + pending + "/confirm")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                                .andExpect(status().isForbidden());

                mockMvc.perform(get("/api/v1/billing/payments")
                                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/billing/receipts")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                                .andExpect(status().isOk());

                mockMvc.perform(post("/api/v1/billing/payments/" + pending + "/void")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"reason":"Sin permiso"}
                                                """))
                                .andExpect(status().isForbidden());
        }

        @Test
        void noAplicaPagoAObligacionesDeOtroEstudiante() throws Exception {
                String otherStudent = createStudent(token, "Luis", "Gómez", "1799999991", "ACTIVE");
                String otherCharge = chargeId(createManualCharge(token, otherStudent, conceptId, ISSUE_DATE, DUE_DATE,
                                null, manualItem(conceptId, "Pensión otro", 1, "100.00", "0")));
                String payment = paymentId(
                                registerPayment(treasuryToken, studentId, "50.00", "CASH", null, "estudiante-1"));

                mockMvc.perform(post("/api/v1/billing/payments/" + payment + "/allocations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(allocationBody("op-estudiante-1", otherCharge, "10.00")))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void consultaYEmiteRecibosInternos() throws Exception {
                MvcResult first = registerPayment(treasuryToken, studentId, "100.00", "CASH", null, "recibo-1");
                MvcResult second = registerPayment(treasuryToken, studentId, "50.00", "CASH", null, "recibo-2");

                String firstNumber = com.jayway.jsonpath.JsonPath.read(
                                first.getResponse().getContentAsString(), "$.receipt.receiptNumber");
                String secondNumber = com.jayway.jsonpath.JsonPath.read(
                                second.getResponse().getContentAsString(), "$.receipt.receiptNumber");
                assertThat(firstNumber).isNotEqualTo(secondNumber);

                String payment = paymentId(first);
                mockMvc.perform(get("/api/v1/billing/receipts/by-payment/" + payment)
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.paymentId").value(payment))
                                .andExpect(jsonPath("$.studentName").value("Pérez, Ana"))
                                .andExpect(jsonPath("$.paymentMethod").value("CASH"));

                mockMvc.perform(get("/api/v1/billing/receipts")
                                .param("paymentId", payment)
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1));

                mockMvc.perform(get("/api/v1/billing/receipts/" + receiptId(first))
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.receiptNumber").value(firstNumber));
        }

        private String createCharge(String key) throws Exception {
                return chargeId(createManualCharge(token, studentId, conceptId, ISSUE_DATE, DUE_DATE, key,
                                manualItem(conceptId, "Pensión", 1, "100.00", "0")));
        }

        private String allocationBody(String operationKey, String chargeId, String amount) {
                return """
                                {"idempotencyKey":"%s","allocations":[{"chargeId":"%s","amount":%s}]}
                                """.formatted(operationKey, chargeId, amount);
        }
}
