package com.akira.educacion.students.api;

import com.akira.educacion.students.domain.StudentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representación pública de un estudiante. {@code notes} solo se incluye si el
 * usuario tiene el permiso STUDENT_NOTES_READ; en caso contrario llega
 * {@code null} y {@code notesRestricted = true}.
 */
public record StudentResponse(
        UUID id,
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        LocalDate birthDate,
        LocalDate admissionDate,
        StudentStatus status,
        String email,
        String phone,
        String address,
        String notes,
        boolean notesRestricted,
        Instant createdAt,
        Instant updatedAt) {
}
