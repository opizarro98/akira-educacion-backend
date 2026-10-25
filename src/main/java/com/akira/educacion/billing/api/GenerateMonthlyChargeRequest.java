package com.akira.educacion.billing.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Generación mensual asistida. Siempre requiere una acción explícita del
 * usuario; no existe ningún proceso programado que emita obligaciones.
 */
public record GenerateMonthlyChargeRequest(
        @NotNull(message = "El estudiante es obligatorio") UUID studentId,
        @NotNull(message = "El concepto es obligatorio") UUID conceptId,
        @NotNull(message = "El período es obligatorio") @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "El período debe usar el formato YYYY-MM") String period,
        BigDecimal amount,
        @Size(max = 500) String description) {
}
