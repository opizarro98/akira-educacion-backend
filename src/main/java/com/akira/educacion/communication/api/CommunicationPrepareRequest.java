package com.akira.educacion.communication.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Registro de una comunicación preparada. {@code saveAsDraft} permite guardarla
 * sin marcarla como lista para abrir WhatsApp.
 */
public record CommunicationPrepareRequest(
        @NotNull UUID studentId,
        @NotNull UUID representativeId,
        @NotNull UUID templateId,
        Boolean includeFinancialInfo,
        @Size(max = 500) String financialNote,
        Boolean saveAsDraft) {
}
