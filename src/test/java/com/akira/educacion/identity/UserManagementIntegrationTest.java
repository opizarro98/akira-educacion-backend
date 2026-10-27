package com.akira.educacion.identity;

import com.akira.educacion.audit.AuditEvent;
import com.akira.educacion.identity.domain.AppUser;
import com.akira.educacion.identity.domain.RoleCodes;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administración de usuarios de la Fase 5B: alcance institucional, roles,
 * activación, contraseñas y auditoría.
 */
class UserManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Secreta123!";

    private Institution institutionA;
    private Institution institutionB;
    private AppUser adminA;

    @BeforeEach
    void setUp() {
        institutionA = newInstitution("Institución A");
        institutionB = newInstitution("Institución B");
        adminA = newInstitutionAdmin(institutionA, "admin.a@test.local", PASSWORD);
        newInstitutionAdmin(institutionB, "admin.b@test.local", PASSWORD);
        newPlatformAdmin("platform@test.local", PASSWORD);
    }

    @Test
    void creaUsuarioConRolesInstitucionales() throws Exception {
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email":"nueva.secretaria@test.local",
                          "fullName":"Nueva Secretaria",
                          "password":"Clave1234!",
                          "roles":["SECRETARY"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nueva.secretaria@test.local"))
                .andExpect(jsonPath("$.institutionId").value(institutionA.getId().toString()))
                .andExpect(jsonPath("$.roles[0]").value(RoleCodes.SECRETARY))
                .andExpect(jsonPath("$.active").value(true));

        AppUser created = appUserRepository.findWithRolesByEmail("nueva.secretaria@test.local").orElseThrow();
        assertThat(auditActions(created.getId())).contains("USER_CREATED");
    }

    @Test
    void rechazaRolDePlataformaEnUsuarioInstitucional() throws Exception {
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email":"intruso@test.local",
                          "fullName":"Intruso",
                          "password":"Clave1234!",
                          "roles":["PLATFORM_ADMIN"]
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editaDatosBasicosYAudita() throws Exception {
        UUID userId = createUser("admin.a@test.local", "secretaria@test.local", RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(put("/api/v1/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"secretaria.editada@test.local","fullName":"Secretaría Editada"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("secretaria.editada@test.local"))
                .andExpect(jsonPath("$.fullName").value("Secretaría Editada"));

        assertThat(auditActions(userId)).contains("USER_UPDATED");
    }

    @Test
    void rechazaCorreoDuplicadoAlEditar() throws Exception {
        UUID userId = createUser("admin.a@test.local", "secretaria@test.local", RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(put("/api/v1/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"admin.a@test.local","fullName":"Duplicado"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void usuarioDesactivadoNoPuedeIniciarSesion() throws Exception {
        UUID userId = createUser("admin.a@test.local", "secretaria@test.local", RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(patch("/api/v1/users/" + userId + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("secretaria@test.local", "Clave1234!")))
                .andExpect(status().isUnauthorized());

        assertThat(auditActions(userId)).contains("USER_DEACTIVATED");
    }

    @Test
    void tokenEmitidoDejaDeValerAlDesactivarUsuario() throws Exception {
        UUID userId = createUser("admin.a@test.local", "secretaria@test.local", RoleCodes.SECRETARY);
        String secretariaToken = login("secretaria@test.local", "Clave1234!");
        String adminToken = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretariaToken)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + userId + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(secretariaToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
    }

    @Test
    void noPermiteDesactivarElPropioUsuario() throws Exception {
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(patch("/api/v1/users/" + adminA.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizaRolesYRegistraAuditoria() throws Exception {
        UUID userId = createUser("admin.a@test.local", "cambiante@test.local", RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(put("/api/v1/users/" + userId + "/roles")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"TREASURY\",\"DIRECTOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        assertThat(auditActions(userId)).contains("USER_ROLES_CHANGED");
    }

    @Test
    void noPermiteQuitarTodosLosRoles() throws Exception {
        UUID userId = createUser("admin.a@test.local", "sinroles@test.local", RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(put("/api/v1/users/" + userId + "/roles")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noGestionaUsuariosDeOtraInstitucion() throws Exception {
        AppUser userB = newUser(institutionB, "secretaria.b@test.local", PASSWORD, RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/users/" + userB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/users/" + userB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cruzado@test.local\",\"fullName\":\"Cruzado\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/users/" + userB.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("institutionId", institutionB.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminRequiereInstitucionExplicita() throws Exception {
        String token = login("platform@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void platformAdminGestionaUsuariosConContextoAuditado() throws Exception {
        newUser(institutionB, "secretaria.b@test.local", PASSWORD, RoleCodes.SECRETARY);
        String token = login("platform@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("institutionId", institutionB.getId().toString())
                .param("search", "secretaria.b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("secretaria.b@test.local"));

        assertThat(auditEventRepository.countByEntityTypeAndEntityId("Institution",
                institutionB.getId().toString())).isPositive();

        mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("institutionId", institutionB.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email":"creado.por.plataforma@test.local",
                          "fullName":"Creado por plataforma",
                          "password":"Clave1234!",
                          "roles":["SECRETARY"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionId").value(institutionB.getId().toString()));
    }

    @Test
    void restablecimientoAdministrativoDePassword() throws Exception {
        UUID userId = createUser("admin.a@test.local", "reset@test.local", RoleCodes.SECRETARY);
        String token = login("admin.a@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/users/" + userId + "/password-reset")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"NuevaClave123!\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("reset@test.local", "Clave1234!")))
                .andExpect(status().isUnauthorized());

        assertThat(login("reset@test.local", "NuevaClave123!")).isNotBlank();
        assertThat(auditActions(userId)).contains("USER_PASSWORD_RESET");
    }

    @Test
    void cambioDePasswordPropio() throws Exception {
        createUser("admin.a@test.local", "propio@test.local", RoleCodes.SECRETARY);
        String token = login("propio@test.local", "Clave1234!");

        mockMvc.perform(put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"Clave1234!","newPassword":"OtraClave123!"}
                        """))
                .andExpect(status().isNoContent());

        assertThat(login("propio@test.local", "OtraClave123!")).isNotBlank();
    }

    @Test
    void rechazaCambioDePasswordConClaveActualIncorrecta() throws Exception {
        createUser("admin.a@test.local", "propio2@test.local", RoleCodes.SECRETARY);
        String token = login("propio2@test.local", "Clave1234!");

        mockMvc.perform(put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"incorrecta","newPassword":"OtraClave123!"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void directorNoPuedeGestionarUsuarios() throws Exception {
        createUser("admin.a@test.local", "director@test.local", RoleCodes.DIRECTOR);
        String token = login("director@test.local", "Clave1234!");

        mockMvc.perform(get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email":"no.autorizado@test.local",
                          "fullName":"No autorizado",
                          "password":"Clave1234!",
                          "roles":["SECRETARY"]
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    private UUID createUser(String adminEmail, String email, String role) throws Exception {
        String token = login(adminEmail, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email":"%s",
                          "fullName":"Usuario de prueba",
                          "password":"Clave1234!",
                          "roles":["%s"]
                        }
                        """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private List<String> auditActions(UUID userId) {
        return auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("AppUser", userId.toString())
                .stream()
                .map(AuditEvent::getAction)
                .toList();
    }
}
