package com.akira.educacion.institution.api;

import com.akira.educacion.institution.domain.InstitutionModuleCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Alta de institución. El campo {@code admin} es opcional y, si se envía,
 * crea el usuario INSTITUTION_ADMIN inicial.
 */
public record CreateInstitutionRequest(
        @NotBlank @Size(max = 200) String legalName,
        @Size(max = 200) String tradeName,
        @Size(max = 30) String taxId,
        @Email @Size(max = 150) String email,
        @Size(max = 40) String phone,
        @Size(max = 200) String website,
        @Size(max = 250) String address,
        @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 60) String timezone,
        @NotBlank @Size(max = 10) String language,
        @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe usar código ISO de 3 letras") String currency,
        @Size(max = 500) String logoUrl,
        @Size(max = 20) String primaryColor,
        @Size(max = 20) String secondaryColor,
        Boolean vatEnabled,
        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") BigDecimal vatRate,
        LocalDate vatEffectiveFrom,
        Set<InstitutionModuleCode> modules,
        @Valid AdminUserRequest admin) {

    public record AdminUserRequest(
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }
}
