package com.akira.educacion.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Una operación puede aplicar el mismo pago a varias obligaciones. La clave de
 * operación permite reintentar la solicitud sin duplicar aplicaciones.
 */
public record ApplyAllocationsRequest(
        @NotEmpty(message = "Debe indicar al menos una aplicación") @Valid List<AllocationItemRequest> allocations,
        @Size(max = 160) String idempotencyKey) {
}
