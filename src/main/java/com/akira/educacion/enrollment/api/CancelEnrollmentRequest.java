package com.akira.educacion.enrollment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelEnrollmentRequest(
        @NotBlank(message = "El motivo de la cancelación es obligatorio") @Size(max = 500) String reason) {
}
