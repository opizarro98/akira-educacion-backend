package com.akira.educacion.finance.api;

import com.akira.educacion.finance.domain.CashMovementSource;
import com.akira.educacion.finance.domain.CashMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementResponse(
        UUID id,
        CashMovementType movementType,
        CashMovementSource sourceType,
        UUID sourceId,
        BigDecimal amount,
        BigDecimal signedAmount,
        String direction,
        String currency,
        Instant occurredAt,
        String description,
        String actorEmail,
        Instant createdAt) {
}
