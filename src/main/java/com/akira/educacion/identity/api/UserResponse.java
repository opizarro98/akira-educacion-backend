package com.akira.educacion.identity.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UUID institutionId,
        Set<String> roles,
        boolean active,
        Instant createdAt) {
}
