package com.akira.educacion.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Obligación manual. La institución nunca se acepta en el cuerpo: proviene de
 * la sesión mediante {@code InstitutionScope}.
 */
public record CreateChargeRequest(
        @NotNull(message = "El estudiante es obligatorio") UUID studentId,
        UUID enrollmentId,
        @NotNull(message = "La fecha de emisión es obligatoria") LocalDate issueDate,
        @NotNull(message = "La fecha de vencimiento es obligatoria") LocalDate dueDate,
        @Size(max = 500) String description,
        @Size(max = 160) String idempotencyKey,
        @NotEmpty(message = "La obligación debe tener al menos un detalle") @Valid List<CreateChargeItemRequest> items) {
}
