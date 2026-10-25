package com.akira.educacion.billing.api;

import java.time.Instant;
import java.util.UUID;

public record PaymentHistoryResponse(
        UUID id,
        String action,
        String details,
        String reason,
        String actorEmail,
        Instant occurredAt) {
}
