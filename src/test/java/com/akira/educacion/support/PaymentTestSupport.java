package com.akira.educacion.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers de la Fase 3B para registrar y operar pagos a través de la API real.
 */
public abstract class PaymentTestSupport extends BillingTestSupport {

    protected MvcResult registerPayment(String token, String studentId, String amount, String method,
            String reference, String idempotencyKey) throws Exception {
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
                          "receivedDate":"2026-10-20",
                          "idempotencyKey":%s
                        }
                        """.formatted(studentId, amount, method, ref, key)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected MvcResult registerTransfer(String token, String studentId, String amount, String reference,
            String idempotencyKey) throws Exception {
        return registerPayment(token, studentId, amount, "BANK_TRANSFER", reference, idempotencyKey);
    }

    protected MvcResult confirmPayment(String token, String paymentId) throws Exception {
        return mockMvc.perform(post("/api/v1/billing/payments/" + paymentId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
    }

    protected String paymentId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected String receiptId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.receipt.id");
    }
}
