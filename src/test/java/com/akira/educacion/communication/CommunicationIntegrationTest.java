package com.akira.educacion.communication;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.CommunicationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunicationIntegrationTest extends CommunicationTestSupport {

    private static final String PASSWORD = "Secreta123!";

    private Institution institution;
    private String token;
    private String secretaryToken;
    private String treasuryToken;
    private String directorToken;
    private String studentId;
    private String representativeId;
    private String representativeWithoutPhone;
    private String representativeUnauthorized;
    private String representativeInvalidPhone;
    private String templateId;

    @BeforeEach
    void setUp() throws Exception {
        institution = newInstitution("Colegio Comunicaciones");

        newInstitutionAdmin(institution, "admin.comunicaciones@test.local", PASSWORD);
        newUser(institution, "secretaria.comunicaciones@test.local", PASSWORD, RoleCodes.SECRETARY);
        newUser(institution, "tesoreria.comunicaciones@test.local", PASSWORD, RoleCodes.TREASURY);
        newUser(institution, "direccion.comunicaciones@test.local", PASSWORD, RoleCodes.DIRECTOR);

        token = login("admin.comunicaciones@test.local", PASSWORD);
        secretaryToken = login("secretaria.comunicaciones@test.local", PASSWORD);
        treasuryToken = login("tesoreria.comunicaciones@test.local", PASSWORD);
        directorToken = login("direccion.comunicaciones@test.local", PASSWORD);

        studentId = createStudent(token, "Ana", "Pérez", "1712345678", "ACTIVE");

        representativeId = createRepresentativeWithPhone(token, "María Pérez", "0999999999");
        linkRepresentative(token, studentId, representativeId, true).andExpect(status().isCreated());

        representativeWithoutPhone = createRepresentativeWithPhone(token, "Sin Teléfono", null);
        linkRepresentative(token, studentId, representativeWithoutPhone, true).andExpect(status().isCreated());

        representativeUnauthorized = createRepresentativeWithPhone(token, "No Autorizada", "0988888888");
        linkRepresentative(token, studentId, representativeUnauthorized, false)
                .andExpect(status().isCreated());

        representativeInvalidPhone = createRepresentativeWithPhone(token, "Teléfono Inválido", "abc");
        linkRepresentative(token, studentId, representativeInvalidPhone, true)
                .andExpect(status().isCreated());

        templateId = readId(createTemplate(token, "AVISO", "Aviso general",
                "Estimado/a {{representante}}:\n\n{{estudiante}} de {{institucion}} & familia.\nFecha: {{fecha}}")
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void creaPlantillaYRechazaMarcadoresNoPermitidos() throws Exception {
        mockMvc.perform(get("/api/v1/communications/templates")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Aviso general"));

        createTemplate(token, "AVISO2", "Aviso general", "Texto")
                .andExpect(status().isConflict());

        createTemplate(token, "MALO", "Plantilla inválida", "Hola {{dato_desconocido}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void generaEnlaceWaMeCorrectamenteCodificado() throws Exception {
        MvcResult result = previewCommunication(secretaryToken, studentId, representativeId, templateId, false, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waPhone").value("593999999999"))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("no confirma")))
                .andReturn();

        String link = readString(result, "$.waLink");
        assertThat(link).startsWith("https://wa.me/593999999999?text=");
        assertThat(link).doesNotContain(" ");
        assertThat(link).doesNotContain("\n");

        String encoded = link.substring(link.indexOf("?text=") + "?text=".length());
        String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        assertThat(decoded).contains("Pérez, Ana").contains("&").contains("\n");
        assertThat(decoded).isEqualTo(readString(result, "$.messageBody"));
    }

    @Test
    void rechazaRepresentanteNoAutorizadoParaComunicaciones() throws Exception {
        previewCommunication(secretaryToken, studentId, representativeUnauthorized, templateId, false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void rechazaTelefonoAusenteOInvalido() throws Exception {
        previewCommunication(secretaryToken, studentId, representativeWithoutPhone, templateId, false, null)
                .andExpect(status().isBadRequest());

        previewCommunication(secretaryToken, studentId, representativeInvalidPhone, templateId, false, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaAccesoCruzadoEntreInstituciones() throws Exception {
        Institution other = newInstitution("Colegio Ajeno Comunicaciones");
        newInstitutionAdmin(other, "admin.ajeno.comunicaciones@test.local", PASSWORD);
        String otherToken = login("admin.ajeno.comunicaciones@test.local", PASSWORD);
        String otherStudent = createStudent(otherToken, "Luis", "Ajeno", "1799999999", "ACTIVE");
        String otherRepresentative = createRepresentativeWithPhone(otherToken, "Representante Ajeno", "0977777777");
        String otherTemplate = readId(createTemplate(otherToken, "OTRO", "Plantilla ajena", "Hola {{estudiante}}")
                .andExpect(status().isCreated()).andReturn());

        previewCommunication(token, otherStudent, otherRepresentative, otherTemplate, false, null)
                .andExpect(status().isNotFound());

        previewCommunication(token, studentId, otherRepresentative, templateId, false, null)
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/communications")
                .param("institutionId", other.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void controlaInformacionFinancieraSensible() throws Exception {
        MvcResult plain = previewCommunication(secretaryToken, studentId, representativeId, templateId, false, null)
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readString(plain, "$.messageBody")).doesNotContain("Detalle financiero");

        MvcResult withNote = previewCommunication(secretaryToken, studentId, representativeId, templateId, true,
                "Saldo pendiente: 100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includeFinancialInfo").value(true))
                .andReturn();
        assertThat(readString(withNote, "$.messageBody")).contains("Saldo pendiente: 100.00");

        previewCommunication(secretaryToken, studentId, representativeId, templateId, true, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void registraComunicacionYTransicionesDeEstado() throws Exception {
        String communication = readId(prepareCommunication(secretaryToken, studentId, representativeId, templateId,
                false, null, false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.createdByEmail").value("secretaria.comunicaciones@test.local"))
                .andExpect(jsonPath("$.templateName").value("Aviso general"))
                .andReturn());

        mockMvc.perform(post("/api/v1/communications/" + communication + "/opened")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPENED"))
                .andExpect(jsonPath("$.openedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/communications/" + communication + "/opened")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPENED"));

        String draft = readId(prepareCommunication(secretaryToken, studentId, representativeId, templateId,
                false, null, true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());

        mockMvc.perform(post("/api/v1/communications/" + draft + "/failed")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"El usuario canceló el envío"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failedReason").value("El usuario canceló el envío"));

        mockMvc.perform(get("/api/v1/communications")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/communications/" + communication)
                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Pérez, Ana"))
                .andExpect(jsonPath("$.representativeName").value("María Pérez"));
    }

    @Test
    void verificaPermisosPorRol() throws Exception {
        createTemplate(secretaryToken, "SEC", "No permitida", "Texto")
                .andExpect(status().isForbidden());

        previewCommunication(directorToken, studentId, representativeId, templateId, false, null)
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/communications")
                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                .andExpect(status().isOk());

        createTemplate(treasuryToken, "TES", "No permitida", "Texto")
                .andExpect(status().isForbidden());

        previewCommunication(treasuryToken, studentId, representativeId, templateId, false, null)
                .andExpect(status().isOk());

        previewCommunication(secretaryToken, studentId, representativeId, templateId, false, null)
                .andExpect(status().isOk());

        newPlatformAdmin("plataforma.comunicaciones@test.local", PASSWORD);
        String platformToken = login("plataforma.comunicaciones@test.local", PASSWORD);
        mockMvc.perform(post("/api/v1/communications/preview")
                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId":"%s","representativeId":"%s","templateId":"%s"}
                        """.formatted(studentId, representativeId, templateId)))
                .andExpect(status().isBadRequest());
    }
}
