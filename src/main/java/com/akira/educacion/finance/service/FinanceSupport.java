package com.akira.educacion.finance.service;

import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Utilidades compartidas por los servicios financieros de la Fase 4.
 */
@Component
@RequiredArgsConstructor
public class FinanceSupport {

    public static final int MONEY_SCALE = 2;

    private final CurrentUser currentUser;
    private final InstitutionRepository institutionRepository;

    public Institution institution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    public BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public UUID currentUserIdOrNull() {
        try {
            return currentUser.userId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public String actorEmail() {
        try {
            return currentUser.email();
        } catch (RuntimeException ex) {
            return "system";
        }
    }

    public String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
