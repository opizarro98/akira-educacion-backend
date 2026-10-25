package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de un pago. La institución proviene de la sesión, nunca del cuerpo.
 * El pago en efectivo puede quedar confirmado de inmediato si el usuario tiene
 * permiso de verificación; las transferencias quedan pendientes de
 * verificación.
 */
public record RegisterPaymentRequest(
        @NotNull(message = "El estudiante es obligatorio") UUID studentId,
        @NotNull(message = "El importe es obligatorio") @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull(message = "El medio de pago es obligatorio") PaymentMethod method,
        @Size(max = 100) String reference,
        LocalDate receivedDate,
        @Size(max = 500) String notes,
        @Size(max = 160) String idempotencyKey,
        @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe usar código ISO de 3 letras") String currency) {
}
