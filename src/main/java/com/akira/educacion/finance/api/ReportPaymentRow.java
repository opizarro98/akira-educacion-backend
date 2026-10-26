package com.akira.educacion.finance.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.billing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportPaymentRow(
        UUID id,
        String studentName,
        LocalDate receivedDate,
        PaymentMethod method,
        String reference,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant confirmedAt,
        Instant voidedAt,
        String voidReason) {
}
