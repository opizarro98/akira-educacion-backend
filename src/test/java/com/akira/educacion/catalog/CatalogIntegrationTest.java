package com.akira.educacion.catalog;

import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.SchoolTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends SchoolTestSupport {

    private static final String PASSWORD = "Secreta123!";

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Institution institution = newInstitution("Colegio Catálogo");
        newInstitutionAdmin(institution, "admin.catalogo@test.local", PASSWORD);
        token = login("admin.catalogo@test.local", PASSWORD);
    }

    @Test
    void creaUnNivelEducativo() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/levels")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Básica Elemental","code":"BE","sortOrder":2,"description":"2do a 4to"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Básica Elemental"))
                .andExpect(jsonPath("$.sortOrder").value(2))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/catalog/levels")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Básica Elemental"}
                        """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/catalog/levels")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void creaUnPeriodoLectivo() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/periods")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"2026-2027","startDate":"2026-09-01","endDate":"2027-06-30"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("2026-2027"))
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2027-06-30"));

        mockMvc.perform(post("/api/v1/catalog/periods")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Inválido","startDate":"2026-09-01","endDate":"2026-08-01"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void creaPlanServiciosYTarifa() throws Exception {
        String transportId = createService(token, "Transporte");
        String foodId = createService(token, "Alimentación");
        String planId = createPlan(token, "Plan Integral", transportId, foodId);
        String rateId = createRate(token, transportId, "35.50");

        mockMvc.perform(get("/api/v1/catalog/plans")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(planId))
                .andExpect(jsonPath("$[0].services.length()").value(2));

        mockMvc.perform(get("/api/v1/catalog/rates")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rateId))
                .andExpect(jsonPath("$[0].serviceName").value("Transporte"))
                .andExpect(jsonPath("$[0].amount").value(35.50));

        mockMvc.perform(put("/api/v1/catalog/plans/" + planId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Plan Integral","serviceIds":["%s"]}
                        """.formatted(foodId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].name").value("Alimentación"));
    }

    @Test
    void rechazaTarifaConVigenciaInvalida() throws Exception {
        String serviceId = createService(token, "Materiales");

        mockMvc.perform(post("/api/v1/catalog/rates")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serviceId":"%s","amount":10.00,"validFrom":"2026-05-01","validTo":"2026-04-01"}
                        """.formatted(serviceId)))
                .andExpect(status().isBadRequest());
    }
}
