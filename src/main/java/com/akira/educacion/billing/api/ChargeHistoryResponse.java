package com.akira.educacion.billing.api;

import java.time.Instant;
import java.util.UUID;

public record ChargeHistoryResponse(
        UUID id,
        String action,
        String fromStatus,
        String toStatus,
        String reason,
        String details,
        String actorEmail,
        Instant occurredAt) {
}
