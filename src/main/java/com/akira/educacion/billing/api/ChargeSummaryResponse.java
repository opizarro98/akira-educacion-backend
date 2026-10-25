package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.ChargeOrigin;
import com.akira.educacion.billing.domain.ChargeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ChargeSummaryResponse(
        UUID id,
        UUID studentId,
        String studentName,
        UUID enrollmentId,
        ChargeOrigin origin,
        ChargeStatus status,
        String currency,
        String description,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        Instant createdAt) {
}
