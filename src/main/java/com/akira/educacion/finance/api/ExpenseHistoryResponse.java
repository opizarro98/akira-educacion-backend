package com.akira.educacion.finance.api;

import java.time.Instant;
import java.util.UUID;

public record ExpenseHistoryResponse(
        UUID id,
        String action,
        String details,
        String reason,
        String actorEmail,
        Instant occurredAt) {
}
