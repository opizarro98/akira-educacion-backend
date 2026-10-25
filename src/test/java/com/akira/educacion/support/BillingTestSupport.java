package com.akira.educacion.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers de la Fase 3A. Igual que en las fases anteriores, los datos se crean
 * a través de la API real para ejercitar controladores, validaciones y
 * permisos.
 */
public abstract class BillingTestSupport extends SchoolTestSupport {

    protected String createConcept(String token, String code, String name, String defaultAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/billing/concepts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":%s,"name":"%s","defaultAmount":%s}
                        """.formatted(jsonString(code), name, defaultAmount)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected MvcResult createManualCharge(String token, String studentId, String conceptId, String issueDate,
            String dueDate, String idempotencyKey, String... itemBodies) throws Exception {
        String items = String.join(",", itemBodies);
        String key = idempotencyKey == null ? "null" : "\"" + idempotencyKey + "\"";
        return mockMvc.perform(post("/api/v1/billing/charges")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "studentId":"%s",
                          "issueDate":"%s",
                          "dueDate":"%s",
                          "description":"Obligación de prueba",
                          "idempotencyKey":%s,
                          "items":[%s]
                        }
                        """.formatted(studentId, issueDate, dueDate, key, items)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected String manualItem(String conceptId, String description, int quantity, String unitPrice,
            String discount) {
        return """
                {"conceptId":"%s","description":"%s","quantity":%d,"unitPrice":%s,"discount":%s}
                """.formatted(conceptId, description, quantity, unitPrice, discount);
    }

    protected String chargeId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
