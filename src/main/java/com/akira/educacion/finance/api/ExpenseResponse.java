package com.akira.educacion.finance.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.finance.domain.ExpenseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        String currency,
        LocalDate expenseDate,
        String description,
        PaymentMethod method,
        String beneficiary,
        String reference,
        ExpenseStatus status,
        String responsibleEmail,
        Instant confirmedAt,
        String confirmedByEmail,
        String voidReason,
        Instant voidedAt,
        String voidedBy,
        Instant createdAt,
        Instant updatedAt) {
}
