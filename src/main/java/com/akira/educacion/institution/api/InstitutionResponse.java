package com.akira.educacion.institution.api;

import com.akira.educacion.institution.domain.InstitutionModuleCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record InstitutionResponse(
        UUID id,
        String legalName,
        String tradeName,
        String taxId,
        String email,
        String phone,
        String website,
        String address,
        String city,
        String country,
        String timezone,
        String language,
        String currency,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        boolean vatEnabled,
        BigDecimal vatRate,
        LocalDate vatEffectiveFrom,
        boolean active,
        Set<InstitutionModuleCode> modules,
        Instant createdAt,
        Instant updatedAt) {
}
