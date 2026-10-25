package com.akira.educacion.billing.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.billing.api.BillingSettingsRequest;
import com.akira.educacion.billing.api.BillingSettingsResponse;
import com.akira.educacion.billing.domain.BillingFrequency;
import com.akira.educacion.billing.domain.BillingSettings;
import com.akira.educacion.billing.repository.BillingSettingsRepository;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Configuración de períodos de cobro por institución. Si la institución aún no
 * tiene configuración se devuelven los valores por defecto sin crearla: la
 * configuración solo se persiste cuando un usuario la guarda explícitamente.
 */
@Service
@RequiredArgsConstructor
public class BillingSettingsService {

    private final BillingSettingsRepository settingsRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public BillingSettingsResponse get(UUID institutionId) {
        return settingsRepository.findByInstitutionId(institutionId)
                .map(this::toResponse)
                .orElseGet(() -> new BillingSettingsResponse(null, BillingFrequency.MONTHLY, 1, 10, true, null));
    }

    @Transactional
    public BillingSettingsResponse update(UUID institutionId, BillingSettingsRequest request) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));

        BillingSettings settings = settingsRepository.findByInstitutionId(institutionId).orElseGet(() -> {
            BillingSettings created = new BillingSettings();
            created.setInstitution(institution);
            return created;
        });

        settings.setFrequency(request.frequency());
        settings.setIssueDay(request.issueDay());
        settings.setDueDays(request.dueDays());
        settings.setActive(request.active() == null || request.active());

        BillingSettings saved = settingsRepository.save(settings);
        auditService.record("BILLING_SETTINGS_UPDATED", "BillingSettings", saved.getId(), institutionId,
                "frequency=" + saved.getFrequency() + "; issueDay=" + saved.getIssueDay()
                        + "; dueDays=" + saved.getDueDays() + "; active=" + saved.isActive());
        return toResponse(saved);
    }

    /**
     * Configuración efectiva para cálculos internos; usa valores por defecto si
     * no existe configuración persistida.
     */
    @Transactional(readOnly = true)
    public BillingSettings effectiveSettings(UUID institutionId) {
        return settingsRepository.findByInstitutionId(institutionId).orElseGet(() -> {
            BillingSettings defaults = new BillingSettings();
            defaults.setFrequency(BillingFrequency.MONTHLY);
            defaults.setIssueDay(1);
            defaults.setDueDays(10);
            defaults.setActive(true);
            return defaults;
        });
    }

    private BillingSettingsResponse toResponse(BillingSettings settings) {
        return new BillingSettingsResponse(settings.getId(), settings.getFrequency(), settings.getIssueDay(),
                settings.getDueDays(), settings.isActive(), settings.getUpdatedAt());
    }
}
