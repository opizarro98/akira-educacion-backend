package com.akira.educacion.enrollment.api;

import com.akira.educacion.enrollment.domain.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentHistoryResponse(
        UUID id,
        String action,
        EnrollmentStatus fromStatus,
        EnrollmentStatus toStatus,
        String reason,
        String actorEmail,
        Instant occurredAt) {
}
