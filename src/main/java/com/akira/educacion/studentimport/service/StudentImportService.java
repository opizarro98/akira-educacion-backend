package com.akira.educacion.studentimport.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import com.akira.educacion.studentimport.api.StudentImportResponse;
import com.akira.educacion.studentimport.api.StudentImportRowResponse;
import com.akira.educacion.studentimport.api.StudentImportSummaryResponse;
import com.akira.educacion.studentimport.domain.ImportDuplicateType;
import com.akira.educacion.studentimport.domain.ImportRowStatus;
import com.akira.educacion.studentimport.domain.StudentImport;
import com.akira.educacion.studentimport.domain.StudentImportRow;
import com.akira.educacion.studentimport.domain.StudentImportStatus;
import com.akira.educacion.studentimport.repository.StudentImportRepository;
import com.akira.educacion.studentimport.repository.StudentImportRowRepository;
import com.akira.educacion.studentimport.service.StudentExcelParser.ParsedFile;
import com.akira.educacion.studentimport.service.StudentExcelParser.ParsedStudentRow;
import com.akira.educacion.students.api.CreateStudentRequest;
import com.akira.educacion.students.api.StudentResponse;
import com.akira.educacion.students.api.UpdateStudentRequest;
import com.akira.educacion.students.domain.Student;
import com.akira.educacion.students.repository.StudentRepository;
import com.akira.educacion.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Flujo de importación de estudiantes en tres etapas:
 * previsualización (sin crear estudiantes), confirmación transaccional y
 * reporte final. Reintentar la confirmación del mismo lote es idempotente.
 */
@Service
@RequiredArgsConstructor
public class StudentImportService {

    private final StudentImportRepository importRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentExcelParser parser;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final InstitutionRepository institutionRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Etapa 1 y 2: carga y previsualización
    // ------------------------------------------------------------------

    @Transactional
    public StudentImportResponse preview(UUID institutionId, MultipartFile file) {
        Institution institution = loadInstitution(institutionId);
        ParsedFile parsed = parser.parse(file);

        Map<String, Integer> seenDocuments = new HashMap<>();
        Map<String, Integer> seenNames = new HashMap<>();
        List<StudentImportRow> rows = new ArrayList<>();

        for (ParsedStudentRow parsedRow : parsed.rows()) {
            StudentImportRow row = buildRow(parsedRow);
            if (parsedRow.valid()) {
                classifyDuplicates(institutionId, row, seenDocuments, seenNames);
            }
            rows.add(row);
        }

        StudentImport batch = new StudentImport();
        batch.setInstitution(institution);
        batch.setFileName(sanitizeFileName(parsed.fileName()));
        batch.setFileSize(parsed.size());
        batch.setFileHash(parsed.hash());
        batch.setStatus(StudentImportStatus.PREVIEWED);
        batch.setCreatedByUserId(currentUserIdOrNull());
        batch.setCreatedByEmail(actorEmail());
        applyCounts(batch, rows);
        StudentImport saved = importRepository.save(batch);

        for (StudentImportRow row : rows) {
            row.setInstitutionId(institutionId);
            row.setImportId(saved.getId());
        }
        rowRepository.saveAll(rows);

        auditService.record("STUDENT_IMPORT_PREVIEWED", "StudentImport", saved.getId(), institutionId,
                "file=" + saved.getFileName() + "; hash=" + saved.getFileHash() + "; total=" + saved.getTotalRows()
                        + "; valid=" + saved.getValidRows() + "; errors=" + saved.getErrorRows()
                        + "; duplicates=" + saved.getDuplicateRows());
        return toResponse(saved, rows);
    }

    // ------------------------------------------------------------------
    // Etapa 3: confirmación e importación
    // ------------------------------------------------------------------

    @Transactional
    public StudentImportResponse confirm(UUID institutionId, UUID importId, boolean allowUpdateExisting) {
        StudentImport batch = importRepository.lockByIdAndInstitutionId(importId, institutionId)
                .orElseThrow(() -> new NotFoundException("La importación no existe en la institución"));
        if (batch.getStatus() == StudentImportStatus.IMPORTED) {
            return toResponse(batch, rowRepository.findByImportIdOrderByRowNumberAsc(batch.getId()));
        }
        if (batch.getStatus() == StudentImportStatus.FAILED) {
            throw new ConflictException("La importación falló y no puede confirmarse");
        }

        batch.setAllowUpdateExisting(allowUpdateExisting);
        List<StudentImportRow> rows = rowRepository.findByImportIdOrderByRowNumberAsc(batch.getId());
        int imported = 0;
        int updated = 0;

        for (StudentImportRow row : rows) {
            if (row.getRowStatus() == ImportRowStatus.VALID) {
                StudentResponse created = studentService.create(institutionId, toCreateRequest(row));
                row.setCreatedStudentId(created.id());
                imported++;
            } else if (row.getRowStatus() == ImportRowStatus.DUPLICATE_EXISTING
                    && allowUpdateExisting && row.getExistingStudentId() != null) {
                Student existing = studentService.find(institutionId, row.getExistingStudentId());
                studentService.update(institutionId, existing.getId(), toUpdateRequest(row, existing));
                row.setCreatedStudentId(existing.getId());
                row.setUpdatedExisting(true);
                updated++;
            }
        }
        rowRepository.saveAll(rows);

        batch.setImportedRows(imported);
        batch.setUpdatedRows(updated);
        batch.setStatus(StudentImportStatus.IMPORTED);
        batch.setConfirmedByEmail(actorEmail());
        batch.setConfirmedAt(Instant.now());
        StudentImport saved = importRepository.save(batch);

        auditService.record("STUDENT_IMPORT_CONFIRMED", "StudentImport", saved.getId(), institutionId,
                "file=" + saved.getFileName() + "; imported=" + imported + "; updated=" + updated
                        + "; skippedErrors=" + saved.getErrorRows() + "; allowUpdateExisting="
                        + allowUpdateExisting);
        return toResponse(saved, rows);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<StudentImportSummaryResponse> list(UUID institutionId, Pageable pageable) {
        Page<StudentImport> page = importRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId,
                pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public StudentImportResponse get(UUID institutionId, UUID importId) {
        StudentImport batch = importRepository.findByIdAndInstitutionId(importId, institutionId)
                .orElseThrow(() -> new NotFoundException("La importación no existe en la institución"));
        return toResponse(batch, rowRepository.findByImportIdOrderByRowNumberAsc(batch.getId()));
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private void classifyDuplicates(UUID institutionId, StudentImportRow row, Map<String, Integer> seenDocuments,
            Map<String, Integer> seenNames) {
        String document = row.getDocumentNumber();
        String nameKey = nameKey(row.getFirstName(), row.getLastName(), row.getBirthDate());

        if (document != null && seenDocuments.containsKey(document)) {
            markDuplicateFile(row, ImportDuplicateType.DOCUMENT,
                    "Identificación duplicada en la fila " + seenDocuments.get(document));
        } else if (nameKey != null && seenNames.containsKey(nameKey)) {
            markDuplicateFile(row, ImportDuplicateType.NAME_BIRTHDATE,
                    "Nombre y fecha de nacimiento duplicados en la fila " + seenNames.get(nameKey));
        } else {
            Optional<Student> existingDocument = document == null ? Optional.empty()
                    : studentRepository.findByInstitutionIdAndDocumentNumberIgnoreCase(institutionId, document);
            if (existingDocument.isPresent()) {
                row.setRowStatus(ImportRowStatus.DUPLICATE_EXISTING);
                row.setDuplicateType(ImportDuplicateType.DOCUMENT);
                row.setExistingStudentId(existingDocument.get().getId());
            } else if (document == null && row.getBirthDate() != null) {
                Optional<Student> existingName = studentRepository
                        .findFirstByInstitutionIdAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDate(
                                institutionId, row.getFirstName(), row.getLastName(), row.getBirthDate());
                if (existingName.isPresent()) {
                    row.setRowStatus(ImportRowStatus.DUPLICATE_EXISTING);
                    row.setDuplicateType(ImportDuplicateType.NAME_BIRTHDATE);
                    row.setExistingStudentId(existingName.get().getId());
                }
            }
        }

        if (document != null) {
            seenDocuments.putIfAbsent(document, row.getRowNumber());
        }
        if (nameKey != null) {
            seenNames.putIfAbsent(nameKey, row.getRowNumber());
        }
    }

    private void markDuplicateFile(StudentImportRow row, ImportDuplicateType type, String error) {
        row.setRowStatus(ImportRowStatus.DUPLICATE_FILE);
        row.setDuplicateType(type);
        List<String> errors = new ArrayList<>(splitErrors(row.getErrors()));
        errors.add(error);
        row.setErrors(String.join("\n", errors));
    }

    private StudentImportRow buildRow(ParsedStudentRow parsed) {
        StudentImportRow row = new StudentImportRow();
        row.setRowNumber(parsed.rowNumber());
        row.setFirstName(parsed.firstName());
        row.setLastName(parsed.lastName());
        row.setDocumentType(parsed.documentType());
        row.setDocumentNumber(parsed.documentNumber());
        row.setBirthDate(parsed.birthDate());
        row.setAdmissionDate(parsed.admissionDate());
        row.setStudentStatus(parsed.studentStatus());
        row.setEmail(parsed.email());
        row.setPhone(parsed.phone());
        row.setAddress(parsed.address());
        row.setRowStatus(parsed.valid() ? ImportRowStatus.VALID : ImportRowStatus.ERROR);
        row.setErrors(parsed.errors().isEmpty() ? null : String.join("\n", parsed.errors()));
        return row;
    }

    private CreateStudentRequest toCreateRequest(StudentImportRow row) {
        return new CreateStudentRequest(
                row.getFirstName(),
                row.getLastName(),
                row.getDocumentType(),
                row.getDocumentNumber(),
                row.getBirthDate(),
                row.getAdmissionDate(),
                row.getStudentStatus(),
                row.getEmail(),
                row.getPhone(),
                row.getAddress(),
                null);
    }

    private UpdateStudentRequest toUpdateRequest(StudentImportRow row, Student existing) {
        return new UpdateStudentRequest(
                row.getFirstName() == null ? existing.getFirstName() : row.getFirstName(),
                row.getLastName() == null ? existing.getLastName() : row.getLastName(),
                existing.getDocumentType(),
                row.getDocumentNumber() == null ? existing.getDocumentNumber() : row.getDocumentNumber(),
                row.getBirthDate() == null ? existing.getBirthDate() : row.getBirthDate(),
                row.getAdmissionDate() == null ? existing.getAdmissionDate() : row.getAdmissionDate(),
                row.getStudentStatus() == null ? existing.getStatus() : row.getStudentStatus(),
                row.getEmail() == null ? existing.getEmail() : row.getEmail(),
                row.getPhone() == null ? existing.getPhone() : row.getPhone(),
                existing.getAddress(),
                null);
    }

    private void applyCounts(StudentImport batch, List<StudentImportRow> rows) {
        int errors = 0;
        int duplicates = 0;
        int valid = 0;
        for (StudentImportRow row : rows) {
            switch (row.getRowStatus()) {
                case VALID -> valid++;
                case ERROR -> errors++;
                default -> duplicates++;
            }
        }
        batch.setTotalRows(rows.size());
        batch.setValidRows(valid);
        batch.setErrorRows(errors);
        batch.setDuplicateRows(duplicates);
    }

    private String nameKey(String firstName, String lastName, LocalDate birthDate) {
        if (firstName == null || lastName == null || birthDate == null) {
            return null;
        }
        return lastName.trim().toLowerCase(Locale.ROOT) + "|" + firstName.trim().toLowerCase(Locale.ROOT)
                + "|" + birthDate;
    }

    private String sanitizeFileName(String fileName) {
        String sanitized = fileName.replaceAll("[\\\\/]", "_").trim();
        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }

    private StudentImportSummaryResponse toSummary(StudentImport batch) {
        return new StudentImportSummaryResponse(
                batch.getId(),
                batch.getFileName(),
                batch.getFileSize(),
                batch.getFileHash(),
                batch.getStatus(),
                batch.getTotalRows(),
                batch.getValidRows(),
                batch.getErrorRows(),
                batch.getDuplicateRows(),
                batch.getImportedRows(),
                batch.getUpdatedRows(),
                batch.isAllowUpdateExisting(),
                batch.getCreatedByEmail(),
                batch.getCreatedAt(),
                batch.getConfirmedByEmail(),
                batch.getConfirmedAt());
    }

    private StudentImportResponse toResponse(StudentImport batch, List<StudentImportRow> rows) {
        List<StudentImportRowResponse> responseRows = rows.stream()
                .map(row -> new StudentImportRowResponse(
                        row.getRowNumber(),
                        row.getFirstName(),
                        row.getLastName(),
                        row.getDocumentType(),
                        row.getDocumentNumber(),
                        row.getBirthDate(),
                        row.getAdmissionDate(),
                        row.getStudentStatus(),
                        row.getEmail(),
                        row.getPhone(),
                        row.getAddress(),
                        row.getRowStatus(),
                        splitErrors(row.getErrors()),
                        row.getDuplicateType(),
                        row.getExistingStudentId(),
                        row.getCreatedStudentId(),
                        row.isUpdatedExisting()))
                .toList();

        return new StudentImportResponse(
                batch.getId(),
                batch.getFileName(),
                batch.getFileSize(),
                batch.getFileHash(),
                batch.getStatus(),
                batch.getTotalRows(),
                batch.getValidRows(),
                batch.getErrorRows(),
                batch.getDuplicateRows(),
                batch.getImportedRows(),
                batch.getUpdatedRows(),
                batch.isAllowUpdateExisting(),
                batch.getCreatedByEmail(),
                batch.getCreatedAt(),
                batch.getConfirmedByEmail(),
                batch.getConfirmedAt(),
                responseRows);
    }

    private List<String> splitErrors(String errors) {
        if (errors == null || errors.isBlank()) {
            return List.of();
        }
        return List.of(errors.split("\\R"));
    }

    private Institution loadInstitution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    private UUID currentUserIdOrNull() {
        try {
            return currentUser.userId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String actorEmail() {
        try {
            return currentUser.email();
        } catch (RuntimeException ex) {
            return "system";
        }
    }
}
