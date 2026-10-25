package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.billing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentSummaryResponse(
        UUID id,
        UUID studentId,
        String studentName,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String reference,
        LocalDate receivedDate,
        PaymentStatus status,
        BigDecimal appliedAmount,
        BigDecimal availableAmount,
        Instant createdAt) {
}
