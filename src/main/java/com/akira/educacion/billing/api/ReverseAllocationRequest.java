package com.akira.educacion.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseAllocationRequest(
        @NotBlank(message = "El motivo de la reversión es obligatorio") @Size(max = 500) String reason) {
}
