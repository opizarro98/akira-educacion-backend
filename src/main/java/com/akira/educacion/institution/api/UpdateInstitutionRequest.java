package com.akira.educacion.institution.api;

import com.akira.educacion.institution.domain.InstitutionModuleCode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Edición de configuración institucional. Solo se aplican los campos enviados
 * (no nulos), lo que permite actualizaciones parciales sin borrar datos.
 */
public record UpdateInstitutionRequest(
        @Size(max = 200) String legalName,
        @Size(max = 200) String tradeName,
        @Size(max = 30) String taxId,
        @Email @Size(max = 150) String email,
        @Size(max = 40) String phone,
        @Size(max = 200) String website,
        @Size(max = 250) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        @Size(max = 60) String timezone,
        @Size(max = 10) String language,
        @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe usar código ISO de 3 letras") String currency,
        @Size(max = 500) String logoUrl,
        @Size(max = 20) String primaryColor,
        @Size(max = 20) String secondaryColor,
        Boolean vatEnabled,
        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") BigDecimal vatRate,
        LocalDate vatEffectiveFrom,
        Set<InstitutionModuleCode> modules,
        Boolean active) {
}
