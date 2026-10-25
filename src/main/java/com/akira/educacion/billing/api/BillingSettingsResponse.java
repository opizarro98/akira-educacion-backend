package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.BillingFrequency;

import java.time.Instant;
import java.util.UUID;

public record BillingSettingsResponse(
        UUID id,
        BillingFrequency frequency,
        int issueDay,
        int dueDays,
        boolean active,
        Instant updatedAt) {
}
