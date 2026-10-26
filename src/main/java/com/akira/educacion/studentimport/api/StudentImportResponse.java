package com.akira.educacion.studentimport.api;

import com.akira.educacion.studentimport.domain.StudentImportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentImportResponse(
        UUID id,
        String fileName,
        long fileSize,
        String fileHash,
        StudentImportStatus status,
        int totalRows,
        int validRows,
        int errorRows,
        int duplicateRows,
        int importedRows,
        int updatedRows,
        boolean allowUpdateExisting,
        String createdByEmail,
        Instant createdAt,
        String confirmedByEmail,
        Instant confirmedAt,
        List<StudentImportRowResponse> rows) {
}
