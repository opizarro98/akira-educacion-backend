package com.akira.educacion.config;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.communication.domain.MessageTemplate;
import com.akira.educacion.communication.repository.MessageTemplateRepository;
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
    private final MessageTemplateRepository messageTemplateRepository;
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
        if (!demoEnabled || isBlank(demoAdminEmail) || isBlank(demoAdminPassword)) {
            return;
        }

        Institution institution = institutionRepository.findAll().stream()
                .filter(candidate -> demoInstitutionName.equals(candidate.getLegalName()))
                .findFirst()
                .orElseGet(this::createDemoInstitution);

        seedTemplates(institution);

        if (!userService.existsByEmail(demoAdminEmail)) {
            userService.create(institution, demoAdminEmail, "Administradora demo", demoAdminPassword,
                    Set.of(RoleCodes.INSTITUTION_ADMIN));
        }
    }

    /**
     * Plantillas de ejemplo. Solo se crean si la institución demo aún no tiene
     * ninguna, de modo que el arranque sea idempotente.
     */
    private void seedTemplates(Institution institution) {
        if (!messageTemplateRepository.search(institution.getId(), false, false).isEmpty()) {
            return;
        }
        createTemplate(institution, "PAGO", "Recordatorio de pago", """
                Estimado/a {{representante}}:

                Le recordamos que {{estudiante}} tiene una obligación pendiente en {{institucion}}.

                {{detalle_financiero}}

                Fecha: {{fecha}}
                """);
        createTemplate(institution, "CITACION", "Citación general", """
                Estimado/a {{representante}}:

                Nos comunicamos de {{institucion}} para conversar sobre {{estudiante}}.
                Por favor, responda a este mensaje para coordinar una reunión.

                Fecha: {{fecha}}
                """);
        createTemplate(institution, "BIENVENIDA", "Bienvenida", """
                Hola {{representante}}:

                Bienvenido/a a {{institucion}}. {{estudiante}} ya forma parte de nuestra comunidad educativa.

                Fecha: {{fecha}}
                """);
    }

    private void createTemplate(Institution institution, String code, String name, String body) {
        MessageTemplate template = new MessageTemplate();
        template.setInstitution(institution);
        template.setCode(code);
        template.setName(name);
        template.setBody(body.stripIndent().trim());
        template.setActive(true);
        MessageTemplate saved = messageTemplateRepository.save(template);
        auditService.record("MESSAGE_TEMPLATE_CREATED", "MessageTemplate", saved.getId(), institution.getId(),
                "origin=dev-seed; name=" + name);
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
