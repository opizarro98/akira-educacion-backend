package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.ChargeStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargeBalanceResponse(
        UUID chargeId,
        ChargeStatus status,
        String currency,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance) {
}
