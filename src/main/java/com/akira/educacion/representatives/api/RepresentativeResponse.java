package com.akira.educacion.representatives.api;

import java.time.Instant;
import java.util.UUID;

public record RepresentativeResponse(
        UUID id,
        String fullName,
        String documentNumber,
        String phone,
        String email,
        String relationship,
        String notes,
        long studentCount,
        Instant createdAt,
        Instant updatedAt) {
}
