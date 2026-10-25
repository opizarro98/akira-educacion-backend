package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.billing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        String receiptNumber,
        long sequenceNumber,
        UUID paymentId,
        UUID studentId,
        String studentName,
        BigDecimal amount,
        String currency,
        Instant issuedAt,
        String issuedByEmail,
        boolean voided,
        String voidReason,
        Instant voidedAt,
        PaymentMethod paymentMethod,
        String paymentReference,
        PaymentStatus paymentStatus,
        LocalDate paymentReceivedDate) {
}
