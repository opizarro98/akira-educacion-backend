package com.akira.educacion.finance.api;

import java.time.Instant;
import java.util.UUID;

public record ExpenseCategoryResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
