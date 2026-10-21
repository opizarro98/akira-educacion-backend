package com.akira.educacion.security;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstitutionAccessIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Secreta123!";

    private Institution institutionA;
    private Institution institutionB;

    @BeforeEach
    void setUp() {
        institutionA = newInstitution("Institución A");
        institutionB = newInstitution("Institución B");
        newInstitutionAdmin(institutionA, "admin.a@test.local", PASSWORD);
        newInstitutionAdmin(institutionB, "admin.b@test.local", PASSWORD);
        newPlatformAdmin("platform@test.local", PASSWORD);
    }

    @Test
    void institutionAdminAccedeASuInstitucion() throws Exception {
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/institutions/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(institutionA.getId().toString()))
                .andExpect(jsonPath("$.legalName").value("Institución A"));
    }

    @Test
    void institutionAdminNoPuedeConsultarOtraInstitucion() throws Exception {
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/institutions/" + institutionB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void institutionAdminNoPuedeListarTodasLasInstituciones() throws Exception {
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/institutions")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void institutionAdminNoPuedeCrearInstituciones() throws Exception {
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/institutions")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"legalName":"Institución C","country":"Ecuador",
                         "timezone":"America/Guayaquil","language":"es","currency":"USD"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminAccedeAlcanceGlobal() throws Exception {
        String token = login("platform@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/institutions/" + institutionB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Institución B"));

        mockMvc.perform(get("/api/v1/institutions")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void usuariosSeListanSoloDeLaPropiaInstitucion() throws Exception {
        newUser(institutionB, "secretaria.b@test.local", PASSWORD, RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("admin.a@test.local"));
    }

    @Test
    void platformAdminNoSeListaComoUsuarioInstitucional() throws Exception {
        String token = login("admin.b@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("admin.b@test.local"));
    }
}
