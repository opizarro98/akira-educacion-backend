package com.akira.educacion.support;

import com.akira.educacion.audit.AuditEventRepository;
import com.akira.educacion.common.config.JwtProperties;
import com.akira.educacion.identity.domain.AppUser;
import com.akira.educacion.identity.domain.Role;
import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.identity.repository.AppUserRepository;
import com.akira.educacion.identity.repository.RoleRepository;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFlywayConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected InstitutionRepository institutionRepository;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected AuditEventRepository auditEventRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtEncoder jwtEncoder;

    @Autowired
    protected JwtProperties jwtProperties;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_event, institution, app_user CASCADE");
    }

    protected Institution newInstitution(String legalName) {
        Institution institution = new Institution();
        institution.setLegalName(legalName);
        institution.setTradeName(legalName);
        institution.setCountry("Ecuador");
        institution.setTimezone("America/Guayaquil");
        institution.setLanguage("es");
        institution.setCurrency("USD");
        institution.setActive(true);
        return institutionRepository.save(institution);
    }

    protected AppUser newUser(Institution institution, String email, String rawPassword, String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Rol no encontrado: " + roleCode));
        AppUser user = new AppUser();
        user.setInstitution(institution);
        user.setEmail(email.toLowerCase());
        user.setFullName("Usuario " + email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setActive(true);
        user.setRoles(new HashSet<>(Set.of(role)));
        return appUserRepository.save(user);
    }

    protected AppUser newInstitutionAdmin(Institution institution, String email, String rawPassword) {
        return newUser(institution, email, rawPassword, RoleCodes.INSTITUTION_ADMIN);
    }

    protected AppUser newPlatformAdmin(String email, String rawPassword) {
        Role role = roleRepository.findByCode(RoleCodes.PLATFORM_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rol PLATFORM_ADMIN no encontrado"));
        AppUser user = new AppUser();
        user.setInstitution(null);
        user.setEmail(email.toLowerCase());
        user.setFullName("Administrador de plataforma");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setActive(true);
        user.setRoles(new HashSet<>(Set.of(role)));
        return appUserRepository.save(user);
    }

    protected String login(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, rawPassword)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    protected String loginBody(String email, String rawPassword) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + rawPassword + "\"}";
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
