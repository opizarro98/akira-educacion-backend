package com.akira.educacion.finance.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro o edición de un egreso. Solo los egresos en borrador son editables.
 */
public record ExpenseRequest(
        @NotNull UUID categoryId,
        @NotNull @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        @NotNull LocalDate expenseDate,
        @NotBlank @Size(max = 500) String description,
        @NotNull PaymentMethod method,
        @Size(max = 200) String beneficiary,
        @Size(max = 100) String reference) {
}
