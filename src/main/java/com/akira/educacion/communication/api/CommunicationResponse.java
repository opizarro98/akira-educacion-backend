package com.akira.educacion.communication.api;

import com.akira.educacion.communication.domain.CommunicationStatus;

import java.time.Instant;
import java.util.UUID;

public record CommunicationResponse(
        UUID id,
        UUID studentId,
        String studentName,
        UUID representativeId,
        String representativeName,
        UUID templateId,
        String templateName,
        String phone,
        String waPhone,
        String messageBody,
        String waLink,
        CommunicationStatus status,
        boolean includeFinancialInfo,
        String financialNote,
        Instant openedAt,
        String failedReason,
        String createdByEmail,
        Instant createdAt,
        Instant updatedAt) {
}
