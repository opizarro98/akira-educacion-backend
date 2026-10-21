package com.akira.educacion.identity.api;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfile user) {
}
