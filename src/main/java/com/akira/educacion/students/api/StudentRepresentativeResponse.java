package com.akira.educacion.students.api;

import java.util.UUID;

/**
 * Vínculo estudiante-representante con las responsabilidades acordadas para
 * ese estudiante en concreto.
 */
public record StudentRepresentativeResponse(
        UUID linkId,
        UUID representativeId,
        String fullName,
        String documentNumber,
        String phone,
        String email,
        String relationship,
        boolean primaryContact,
        boolean authorizedForCommunications,
        boolean authorizedForBilling) {
}
