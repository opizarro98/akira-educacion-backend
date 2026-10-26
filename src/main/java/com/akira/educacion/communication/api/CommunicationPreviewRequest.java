package com.akira.educacion.communication.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Datos para preparar una comunicación. La institución nunca proviene de este
 * cuerpo: se resuelve desde el usuario autenticado o del contexto explícito de
 * {@code PLATFORM_ADMIN}.
 */
public record CommunicationPreviewRequest(
        @NotNull UUID studentId,
        @NotNull UUID representativeId,
        @NotNull UUID templateId,
        Boolean includeFinancialInfo,
        @Size(max = 500) String financialNote) {
}
