package com.akira.educacion.config;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.identity.service.UserService;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.domain.InstitutionModuleCode;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Datos de arranque solo para desarrollo local. Se desactiva con
 * app.seed.enabled=false y nunca se ejecuta con el perfil prod.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final InstitutionRepository institutionRepository;
    private final UserService userService;
    private final AuditService auditService;

    @Value("${app.seed.platform-admin-email:}")
    private String platformAdminEmail;

    @Value("${app.seed.platform-admin-password:}")
    private String platformAdminPassword;

    @Value("${app.seed.demo-enabled:false}")
    private boolean demoEnabled;

    @Value("${app.seed.demo-institution-name:Institución Demo}")
    private String demoInstitutionName;

    @Value("${app.seed.demo-admin-email:}")
    private String demoAdminEmail;

    @Value("${app.seed.demo-admin-password:}")
    private String demoAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPlatformAdmin();
        seedDemoInstitution();
    }

    private void seedPlatformAdmin() {
        if (isBlank(platformAdminEmail) || isBlank(platformAdminPassword)
                || userService.existsByEmail(platformAdminEmail)) {
            return;
        }
        userService.createPlatformAdmin(platformAdminEmail, "Administrador de plataforma", platformAdminPassword);
    }

    private void seedDemoInstitution() {
        if (!demoEnabled || isBlank(demoAdminEmail) || isBlank(demoAdminPassword)
                || userService.existsByEmail(demoAdminEmail)) {
            return;
        }

        Institution institution = institutionRepository.findAll().stream()
                .filter(candidate -> demoInstitutionName.equals(candidate.getLegalName()))
                .findFirst()
                .orElseGet(this::createDemoInstitution);

        userService.create(institution, demoAdminEmail, "Administradora demo", demoAdminPassword,
                Set.of(RoleCodes.INSTITUTION_ADMIN));
    }

    private Institution createDemoInstitution() {
        Institution institution = new Institution();
        institution.setLegalName(demoInstitutionName);
        institution.setTradeName(demoInstitutionName);
        institution.setCountry("Ecuador");
        institution.setCity("Quito");
        institution.setTimezone("America/Guayaquil");
        institution.setLanguage("es");
        institution.setCurrency("USD");
        institution.setVatEnabled(false);
        institution.setModules(EnumSet.allOf(InstitutionModuleCode.class));
        institution.setActive(true);
        Institution saved = institutionRepository.save(institution);
        auditService.record("INSTITUTION_CREATED", "Institution", saved.getId(), saved.getId(),
                "origin=dev-seed; legalName=" + saved.getLegalName());
        return saved;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
