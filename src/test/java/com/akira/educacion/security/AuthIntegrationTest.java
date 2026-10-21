package com.akira.educacion.security;

import com.akira.educacion.identity.domain.AppUser;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Secreta123!";

    @Test
    void loginExitosoDevuelveTokenYPerfil() throws Exception {
        Institution institution = newInstitution("Colegio Uno");
        newInstitutionAdmin(institution, "admin1@test.local", PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("admin1@test.local", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("admin1@test.local"))
                .andExpect(jsonPath("$.user.roles[0]").value("INSTITUTION_ADMIN"))
                .andExpect(jsonPath("$.user.institutionId").value(institution.getId().toString()))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void loginFallidoDevuelveProblemDetail() throws Exception {
        Institution institution = newInstitution("Colegio Uno");
        newInstitutionAdmin(institution, "admin1@test.local", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("admin1@test.local", "incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void contrasenaSeAlmacenaConHash() {
        Institution institution = newInstitution("Colegio Uno");
        AppUser user = newInstitutionAdmin(institution, "admin1@test.local", PASSWORD);

        AppUser stored = appUserRepository.findWithRolesByEmail("admin1@test.local").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(stored.getId()).isEqualTo(user.getId());
    }

    @Test
    void perfilRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenInvalidoEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoEsRechazado() throws Exception {
        Institution institution = newInstitution("Colegio Uno");
        AppUser user = newInstitutionAdmin(institution, "admin1@test.local", PASSWORD);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(user.getId().toString())
                .issuedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .claim("authorities", java.util.List.of("ROLE_INSTITUTION_ADMIN", "INSTITUTION_READ"))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilAutenticadoDevuelveDatosDelToken() throws Exception {
        Institution institution = newInstitution("Colegio Uno");
        newInstitutionAdmin(institution, "admin1@test.local", PASSWORD);
        String token = login("admin1@test.local", PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin1@test.local"))
                .andExpect(jsonPath("$.permissions",
                        org.hamcrest.Matchers.hasItem("INSTITUTION_READ")));
    }
}
