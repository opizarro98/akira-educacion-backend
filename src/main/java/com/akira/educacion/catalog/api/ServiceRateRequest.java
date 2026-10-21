package com.akira.educacion.catalog.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceRateRequest(
        @NotNull(message = "El servicio es obligatorio") UUID serviceId,
        @NotNull(message = "El monto es obligatorio") @DecimalMin(value = "0.00", message = "El monto no puede ser negativo") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull(message = "La vigencia inicial es obligatoria") LocalDate validFrom,
        LocalDate validTo,
        Boolean active) {
}
