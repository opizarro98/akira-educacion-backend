package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.ChargeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAllocationResponse(
        UUID id,
        UUID paymentId,
        UUID chargeId,
        String chargeDescription,
        ChargeStatus chargeStatus,
        BigDecimal amount,
        boolean reversed,
        Instant reversedAt,
        String reversedBy,
        String reversalReason,
        Instant createdAt) {
}
