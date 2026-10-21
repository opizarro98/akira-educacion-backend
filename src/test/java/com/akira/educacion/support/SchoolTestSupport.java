package com.akira.educacion.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers para las pruebas de la Fase 2. Crean datos a través de la API real
 * (no de los repositorios) para ejercitar controladores, DTOs y permisos.
 */
public abstract class SchoolTestSupport extends AbstractIntegrationTest {

    protected static final String ADMISSION_DATE = "2026-03-01";

    protected String createStudent(String token, String firstName, String lastName, String document,
            String status) throws Exception {
        String body = """
                {
                  "firstName": "%s",
                  "lastName": "%s",
                  "documentNumber": %s,
                  "admissionDate": "%s",
                  "status": "%s"
                }
                """.formatted(firstName, lastName, jsonString(document), ADMISSION_DATE, status);
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String createRepresentative(String token, String fullName, String document) throws Exception {
        String body = """
                {
                  "fullName": "%s",
                  "documentNumber": %s,
                  "phone": "0999999999",
                  "email": "representante@test.local",
                  "relationship": "Madre"
                }
                """.formatted(fullName, jsonString(document));
        MvcResult result = mockMvc.perform(post("/api/v1/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String createLevel(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/catalog/levels")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","code":"N1","sortOrder":1}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String createPeriod(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/catalog/periods")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","startDate":"2026-03-01","endDate":"2026-12-15"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String createService(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/catalog/services")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","code":"SRV"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String createRate(String token, String serviceId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/catalog/rates")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serviceId":"%s","amount":%s,"validFrom":"2026-01-01"}
                        """.formatted(serviceId, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String createPlan(String token, String name, String... serviceIds) throws Exception {
        String services = String.join(",", java.util.Arrays.stream(serviceIds)
                .map(id -> "\"" + id + "\"")
                .toList());
        MvcResult result = mockMvc.perform(post("/api/v1/catalog/plans")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":"Plan de prueba","serviceIds":[%s]}
                        """.formatted(name, services)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String createEnrollment(String token, String studentId, String levelId, String planId,
            String periodId, String price, String extraServiceId, String extraPrice) throws Exception {
        String period = periodId == null ? "null" : "\"" + periodId + "\"";
        String plan = planId == null ? "null" : "\"" + planId + "\"";
        String services = extraServiceId == null
                ? "[]"
                : """
                        [{"serviceId":"%s","agreedPrice":%s,"quantity":1}]
                        """.formatted(extraServiceId, extraPrice);
        String body = """
                {
                  "studentId":"%s",
                  "educationalLevelId":"%s",
                  "planId":%s,
                  "academicPeriodId":%s,
                  "startDate":"2026-03-01",
                  "agreedPrice":%s,
                  "services":%s
                }
                """.formatted(studentId, levelId, plan, period, price, services);
        MvcResult result = mockMvc.perform(post("/api/v1/enrollments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    protected String readId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected static BigDecimal readDecimal(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return new BigDecimal(value.toString());
    }

    protected static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    protected static String uuid(UUID value) {
        return value.toString();
    }
}
