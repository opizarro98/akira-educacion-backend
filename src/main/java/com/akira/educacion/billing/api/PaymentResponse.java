package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.billing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
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
        String registeredByEmail,
        String confirmedByEmail,
        Instant confirmedAt,
        String notes,
        String idempotencyKey,
        String voidReason,
        Instant voidedAt,
        String voidedBy,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentAllocationResponse> allocations,
        ReceiptResponse receipt) {
}
