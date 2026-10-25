package com.akira.educacion.billing.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BillingConceptResponse(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal defaultAmount,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
