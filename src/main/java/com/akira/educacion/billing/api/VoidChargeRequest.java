package com.akira.educacion.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidChargeRequest(
        @NotBlank(message = "El motivo de anulación es obligatorio") @Size(max = 500) String reason) {
}
