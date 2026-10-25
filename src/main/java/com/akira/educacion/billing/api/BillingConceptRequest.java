package com.akira.educacion.billing.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BillingConceptRequest(
        @Size(max = 40, message = "El código no puede superar 40 caracteres") String code,
        @NotBlank(message = "El nombre del concepto es obligatorio") @Size(max = 150) String name,
        @Size(max = 255) String description,
        @DecimalMin(value = "0.00", message = "El importe por defecto no puede ser negativo") @Digits(integer = 10, fraction = 2) BigDecimal defaultAmount,
        Boolean active) {
}
