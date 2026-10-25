package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.ChargeOrigin;
import com.akira.educacion.billing.domain.ChargeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChargeResponse(
        UUID id,
        UUID studentId,
        String studentName,
        UUID enrollmentId,
        ChargeOrigin origin,
        String idempotencyKey,
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
        UUID responsibleUserId,
        String responsibleEmail,
        String voidReason,
        Instant voidedAt,
        String voidedBy,
        Instant createdAt,
        Instant updatedAt,
        List<ChargeItemResponse> items) {
}
