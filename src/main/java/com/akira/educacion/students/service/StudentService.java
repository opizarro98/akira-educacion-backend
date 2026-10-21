package com.akira.educacion.students.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.api.SearchPatterns;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.enrollment.repository.EnrollmentRepository;
import com.akira.educacion.identity.domain.PermissionCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import com.akira.educacion.students.api.CreateStudentRequest;
import com.akira.educacion.students.api.StudentResponse;
import com.akira.educacion.students.api.UpdateStudentRequest;
import com.akira.educacion.students.domain.Student;
import com.akira.educacion.students.domain.StudentStatus;
import com.akira.educacion.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional
    public StudentResponse create(UUID institutionId, CreateStudentRequest request) {
        Institution institution = loadInstitution(institutionId);

        Student student = new Student();
        student.setInstitution(institution);
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setDocumentType(trimToNull(request.documentType()));
        student.setDocumentNumber(validateDocument(institutionId, request.documentNumber(), null));
        student.setBirthDate(request.birthDate());
        student.setAdmissionDate(request.admissionDate());
        student.setStatus(request.status() == null ? StudentStatus.PRE_REGISTERED : request.status());
        student.setEmail(normalizeEmail(request.email()));
        student.setPhone(trimToNull(request.phone()));
        student.setAddress(trimToNull(request.address()));
        student.setNotes(requireNotesPermission(request.notes()));

        Student saved = studentRepository.save(student);
        auditService.record("STUDENT_CREATED", "Student", saved.getId(), institutionId,
                "status=" + saved.getStatus());
        return toResponse(saved);
    }

    @Transactional
    public StudentResponse update(UUID institutionId, UUID id, UpdateStudentRequest request) {
        Student student = find(institutionId, id);

        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setDocumentType(trimToNull(request.documentType()));
        student.setDocumentNumber(validateDocument(institutionId, request.documentNumber(), id));
        student.setBirthDate(request.birthDate());
        student.setAdmissionDate(request.admissionDate());
        student.setStatus(request.status());
        student.setEmail(normalizeEmail(request.email()));
        student.setPhone(trimToNull(request.phone()));
        student.setAddress(trimToNull(request.address()));
        if (request.notes() != null) {
            student.setNotes(requireNotesPermission(request.notes()));
        }

        Student saved = studentRepository.save(student);
        auditService.record("STUDENT_UPDATED", "Student", saved.getId(), institutionId,
                "status=" + saved.getStatus());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public StudentResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> list(UUID institutionId, String search, StudentStatus status,
            Pageable pageable) {
        String statusPattern = status == null ? "%" : status.name();
        Page<Student> page = studentRepository.search(institutionId, statusPattern, SearchPatterns.like(search),
                pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    /**
     * Elimina un estudiante solo si no tiene información histórica asociada.
     * Con matrículas registradas la operación se rechaza: el ciclo correcto es
     * cambiar el estado a WITHDRAWN.
     */
    @Transactional
    public void delete(UUID institutionId, UUID id) {
        Student student = find(institutionId, id);
        if (enrollmentRepository.existsByStudentId(id)) {
            throw new ConflictException(
                    "El estudiante tiene matrículas registradas; cámbielo a retirado en lugar de eliminarlo");
        }
        studentRepository.delete(student);
        auditService.record("STUDENT_DELETED", "Student", id, institutionId, null);
    }

    /**
     * Devuelve el estudiante validando que pertenezca a la institución.
     */
    @Transactional(readOnly = true)
    public Student find(UUID institutionId, UUID id) {
        return studentRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El estudiante no existe en la institución"));
    }

    private String validateDocument(UUID institutionId, String documentNumber, UUID currentId) {
        String normalized = documentNumber == null || documentNumber.isBlank()
                ? null
                : documentNumber.trim().toUpperCase(Locale.ROOT);
        if (normalized == null) {
            return null;
        }
        boolean duplicated = currentId == null
                ? studentRepository.existsByInstitutionIdAndDocumentNumberIgnoreCase(institutionId, normalized)
                : studentRepository.existsByInstitutionIdAndDocumentNumberIgnoreCaseAndIdNot(institutionId,
                        normalized, currentId);
        if (duplicated) {
            throw new ConflictException("Ya existe un estudiante con esa identificación en la institución");
        }
        return normalized;
    }

    private String requireNotesPermission(String notes) {
        if (notes == null) {
            return null;
        }
        if (!canReadNotes()) {
            throw new AccessDeniedException("No tiene permiso para registrar observaciones de estudiantes");
        }
        return trimToNull(notes);
    }

    private boolean canReadNotes() {
        return currentUser.authorities().contains(PermissionCodes.STUDENT_NOTES_READ);
    }

    private StudentResponse toResponse(Student student) {
        boolean restricted = !canReadNotes();
        return new StudentResponse(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getDocumentType(),
                student.getDocumentNumber(),
                student.getBirthDate(),
                student.getAdmissionDate(),
                student.getStatus(),
                student.getEmail(),
                student.getPhone(),
                student.getAddress(),
                restricted ? null : student.getNotes(),
                restricted,
                student.getCreatedAt(),
                student.getUpdatedAt());
    }

    private Institution loadInstitution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
