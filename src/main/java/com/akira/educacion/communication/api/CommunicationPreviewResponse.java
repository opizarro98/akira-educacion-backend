package com.akira.educacion.communication.api;

import java.util.List;
import java.util.UUID;

/**
 * Vista previa del mensaje exacto que se abrirá en WhatsApp. No persiste nada.
 */
public record CommunicationPreviewResponse(
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
        boolean includeFinancialInfo,
        String financialNote,
        List<String> warnings) {
}
