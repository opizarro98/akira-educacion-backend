package com.akira.educacion.institution;

import com.akira.educacion.audit.AuditEvent;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstitutionManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Secreta123!";

    private Institution existing;
    private String platformToken;

    @BeforeEach
    void setUp() throws Exception {
        existing = newInstitution("Institución Existente");
        newInstitutionAdmin(existing, "admin.existente@test.local", PASSWORD);
        newPlatformAdmin("platform@test.local", PASSWORD);
        platformToken = login("platform@test.local", PASSWORD);
    }

    @Test
    void platformAdminCreaInstitucionConAdministrador() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/institutions")
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "legalName": "Colegio Nuevo",
                          "tradeName": "Colegio Nuevo",
                          "taxId": "1790000000001",
                          "email": "contacto@nuevo.test",
                          "country": "Ecuador",
                          "city": "Quito",
                          "timezone": "America/Guayaquil",
                          "language": "es",
                          "currency": "USD",
                          "vatEnabled": false,
                          "admin": {
                            "email": "admin.nuevo@test.local",
                            "fullName": "Administrador Nuevo",
                            "password": "Secreta123!"
                          }
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legalName").value("Colegio Nuevo"))
                .andExpect(jsonPath("$.modules").isNotEmpty())
                .andReturn();

        String institutionId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(appUserRepository.findWithRolesByEmail("admin.nuevo@test.local")).isPresent();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("admin.nuevo@test.local", "Secreta123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.institutionId").value(institutionId))
                .andExpect(jsonPath("$.user.roles[0]").value("INSTITUTION_ADMIN"));
    }

    @Test
    void institutionAdminEditaSuPropiaConfiguracion() throws Exception {
        String token = login("admin.existente@test.local", PASSWORD);

        mockMvc.perform(put("/api/v1/institutions/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tradeName":"Nombre Comercial Editado",
                         "email":"nuevo-contacto@test.local",
                         "phone":"0999999999",
                         "logoUrl":"https://cdn.test/logo.png",
                         "primaryColor":"#123456"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("Nombre Comercial Editado"))
                .andExpect(jsonPath("$.email").value("nuevo-contacto@test.local"))
                .andExpect(jsonPath("$.primaryColor").value("#123456"));

        Institution stored = institutionRepository.findById(existing.getId()).orElseThrow();
        assertThat(stored.getTradeName()).isEqualTo("Nombre Comercial Editado");
        assertThat(stored.getPhone()).isEqualTo("0999999999");
    }

    @Test
    void institutionAdminNoPuedeEditarOtraInstitucion() throws Exception {
        Institution other = newInstitution("Institución Ajena");
        String token = login("admin.existente@test.local", PASSWORD);

        mockMvc.perform(put("/api/v1/institutions/" + other.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeName\":\"Intento\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuracionDeIvaSePersiste() throws Exception {
        String token = login("admin.existente@test.local", PASSWORD);

        mockMvc.perform(put("/api/v1/institutions/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vatEnabled":true,"vatRate":15.00,"vatEffectiveFrom":"2026-01-01"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vatEnabled").value(true))
                .andExpect(jsonPath("$.vatRate").value(15.00))
                .andExpect(jsonPath("$.vatEffectiveFrom").value("2026-01-01"));

        mockMvc.perform(get("/api/v1/institutions/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vatEnabled").value(true))
                .andExpect(jsonPath("$.vatRate").value(15.00));
    }

    @Test
    void ivaHabilitadoSinTasaEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"legalName":"Institución Sin Tasa","country":"Ecuador",
                         "timezone":"America/Guayaquil","language":"es","currency":"USD",
                         "vatEnabled":true}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void identificacionTributariaDuplicadaEsRechazada() throws Exception {
        Institution withTaxId = newInstitution("Con RUC");
        withTaxId.setTaxId("1790000000001");
        institutionRepository.save(withTaxId);

        mockMvc.perform(post("/api/v1/institutions")
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"legalName":"Duplicada","taxId":"1790000000001","country":"Ecuador",
                         "timezone":"America/Guayaquil","language":"es","currency":"USD"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void auditoriaRegistraCreacionYEdicion() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/institutions")
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"legalName":"Auditada","country":"Ecuador",
                         "timezone":"America/Guayaquil","language":"es","currency":"USD"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();

        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/v1/institutions/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeName\":\"Auditada Editada\"}"))
                .andExpect(status().isOk());

        List<AuditEvent> events = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Institution", id);
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        assertThat(events).extracting(AuditEvent::getAction)
                .contains("INSTITUTION_CREATED", "INSTITUTION_UPDATED");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getEntityId()).isEqualTo(id);
            assertThat(event.getActorEmail()).isEqualTo("platform@test.local");
        });
        assertThat(auditEventRepository.countByEntityTypeAndEntityId("Institution", id)).isGreaterThanOrEqualTo(2);
        assertThat(UUID.fromString(id)).isNotNull();
    }
}
