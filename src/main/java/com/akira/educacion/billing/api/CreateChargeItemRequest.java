package com.akira.educacion.billing.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateChargeItemRequest(
        @NotNull(message = "El concepto es obligatorio") UUID conceptId,
        @Size(max = 255) String description,
        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer quantity,
        @NotNull(message = "El precio unitario es obligatorio") @DecimalMin(value = "0.00", message = "El precio unitario no puede ser negativo") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo") @Digits(integer = 10, fraction = 2) BigDecimal discount) {
}
