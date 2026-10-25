package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.BillingFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuración de períodos de cobro. La frecuencia y las fechas son
 * configurables por institución; la generación nunca es automática.
 */
public record BillingSettingsRequest(
        @NotNull(message = "La frecuencia es obligatoria") BillingFrequency frequency,
        @NotNull @Min(value = 1, message = "El día de emisión debe estar entre 1 y 28") @Max(value = 28, message = "El día de emisión debe estar entre 1 y 28") Integer issueDay,
        @NotNull @Min(value = 0, message = "Los días de vencimiento no pueden ser negativos") @Max(value = 120, message = "Los días de vencimiento no pueden superar 120") Integer dueDays,
        Boolean active) {
}
