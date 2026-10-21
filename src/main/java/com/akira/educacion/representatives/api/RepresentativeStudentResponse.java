package com.akira.educacion.representatives.api;

import com.akira.educacion.students.domain.StudentStatus;

import java.util.UUID;

/**
 * Estudiante asociado a un representante, con las responsabilidades que el
 * vínculo tiene para ese estudiante.
 */
public record RepresentativeStudentResponse(
        UUID studentId,
        String fullName,
        String documentNumber,
        StudentStatus status,
        boolean primaryContact,
        boolean authorizedForCommunications,
        boolean authorizedForBilling) {
}
