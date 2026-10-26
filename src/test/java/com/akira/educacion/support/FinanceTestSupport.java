package com.akira.educacion.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers de la Fase 4 para categorías, egresos y movimientos de caja.
 */
public abstract class FinanceTestSupport extends PaymentTestSupport {

    protected MvcResult createExpenseCategory(String token, String code, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/finance/expense-categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":%s,"name":"%s","description":"Categoría de prueba","active":true}
                        """.formatted(jsonString(code), name)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected MvcResult createExpense(String token, String categoryId, String amount, LocalDate expenseDate,
            String method, String description) throws Exception {
        return mockMvc.perform(post("/api/v1/finance/expenses")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "categoryId":"%s",
                          "amount":%s,
                          "expenseDate":"%s",
                          "description":"%s",
                          "method":"%s",
                          "beneficiary":"Proveedor de prueba",
                          "reference":"FAC-001"
                        }
                        """.formatted(categoryId, amount, expenseDate, description, method)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected MvcResult confirmExpense(String token, String expenseId) throws Exception {
        return mockMvc.perform(post("/api/v1/finance/expenses/" + expenseId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected MvcResult voidExpense(String token, String expenseId, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/finance/expenses/" + expenseId + "/void")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"%s"}
                        """.formatted(reason)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /**
     * Registra un pago con fecha de recepción dinámica para que los reportes
     * por período sean estables independientemente del día de ejecución.
     */
    protected MvcResult registerPaymentOn(String token, String studentId, String amount, String method,
            String reference, String idempotencyKey, LocalDate receivedDate) throws Exception {
        String ref = reference == null ? "null" : "\"" + reference + "\"";
        String key = idempotencyKey == null ? "null" : "\"" + idempotencyKey + "\"";
        return mockMvc.perform(post("/api/v1/billing/payments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "studentId":"%s",
                          "amount":%s,
                          "method":"%s",
                          "reference":%s,
                          "receivedDate":"%s",
                          "idempotencyKey":%s
                        }
                        """.formatted(studentId, amount, method, ref, receivedDate, key)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected String expenseId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected static String currentPeriodFrom() {
        return LocalDate.now().withDayOfMonth(1).toString();
    }

    protected static String currentPeriodTo() {
        return LocalDate.now().toString();
    }
}
