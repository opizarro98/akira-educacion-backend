package com.akira.educacion.communication.api;

import java.time.Instant;
import java.util.UUID;

public record MessageTemplateResponse(
        UUID id,
        String code,
        String name,
        String body,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
