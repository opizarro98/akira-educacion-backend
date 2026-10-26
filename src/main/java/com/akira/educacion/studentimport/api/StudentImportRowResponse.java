package com.akira.educacion.studentimport.api;

import com.akira.educacion.studentimport.domain.ImportDuplicateType;
import com.akira.educacion.studentimport.domain.ImportRowStatus;
import com.akira.educacion.students.domain.StudentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentImportRowResponse(
        int rowNumber,
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        LocalDate birthDate,
        LocalDate admissionDate,
        StudentStatus studentStatus,
        String email,
        String phone,
        String address,
        ImportRowStatus rowStatus,
        List<String> errors,
        ImportDuplicateType duplicateType,
        UUID existingStudentId,
        UUID createdStudentId,
        boolean updatedExisting) {
}
