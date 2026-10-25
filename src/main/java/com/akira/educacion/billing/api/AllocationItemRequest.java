package com.akira.educacion.billing.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AllocationItemRequest(
        @NotNull(message = "La obligación es obligatoria") UUID chargeId,
        @NotNull(message = "El importe es obligatorio") @DecimalMin(value = "0.01", message = "El importe aplicado debe ser mayor que cero") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
}
