package com.akira.educacion.identity.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfile(
        UUID id,
        String email,
        String fullName,
        UUID institutionId,
        String institutionName,
        Set<String> roles,
        Set<String> permissions,
        boolean platformAdmin) {
}
