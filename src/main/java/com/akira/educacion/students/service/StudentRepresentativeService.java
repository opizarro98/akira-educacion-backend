package com.akira.educacion.students.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.representatives.domain.Representative;
import com.akira.educacion.representatives.service.RepresentativeService;
import com.akira.educacion.students.api.LinkRepresentativeRequest;
import com.akira.educacion.students.api.StudentRepresentativeResponse;
import com.akira.educacion.students.domain.Student;
import com.akira.educacion.students.domain.StudentRepresentative;
import com.akira.educacion.students.repository.StudentRepresentativeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gestión de los vínculos entre estudiantes y representantes.
 */
@Service
@RequiredArgsConstructor
public class StudentRepresentativeService {

    private final StudentRepresentativeRepository linkRepository;
    private final StudentService studentService;
    private final RepresentativeService representativeService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StudentRepresentativeResponse> list(UUID institutionId, UUID studentId) {
        studentService.find(institutionId, studentId);
        return linkRepository.findByStudentIdOrderByPrimaryContactDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StudentRepresentativeResponse link(UUID institutionId, UUID studentId, LinkRepresentativeRequest request) {
        Student student = studentService.find(institutionId, studentId);
        Representative representative = resolveRepresentative(institutionId, request);

        if (linkRepository.findByStudentIdAndRepresentativeId(studentId, representative.getId()).isPresent()) {
            throw new ConflictException("El representante ya está asociado al estudiante");
        }

        StudentRepresentative link = new StudentRepresentative();
        link.setInstitution(student.getInstitution());
        link.setStudent(student);
        link.setRepresentative(representative);
        applyFlags(link, request, studentId);
        StudentRepresentative saved = linkRepository.save(link);

        auditService.record("STUDENT_REPRESENTATIVE_LINKED", "Student", studentId, institutionId,
                "representativeId=" + representative.getId());
        return toResponse(saved);
    }

    @Transactional
    public StudentRepresentativeResponse updateLink(UUID institutionId, UUID studentId, UUID representativeId,
            LinkRepresentativeRequest request) {
        studentService.find(institutionId, studentId);
        representativeService.find(institutionId, representativeId);

        StudentRepresentative link = linkRepository
                .findByStudentIdAndRepresentativeId(studentId, representativeId)
                .orElseThrow(() -> new NotFoundException("El representante no está asociado al estudiante"));

        applyFlags(link, request, studentId);
        StudentRepresentative saved = linkRepository.save(link);
        auditService.record("STUDENT_REPRESENTATIVE_UPDATED", "Student", studentId, institutionId,
                "representativeId=" + representativeId);
        return toResponse(saved);
    }

    @Transactional
    public void unlink(UUID institutionId, UUID studentId, UUID representativeId) {
        studentService.find(institutionId, studentId);
        StudentRepresentative link = linkRepository
                .findByStudentIdAndRepresentativeId(studentId, representativeId)
                .orElseThrow(() -> new NotFoundException("El representante no está asociado al estudiante"));
        linkRepository.delete(link);
        auditService.record("STUDENT_REPRESENTATIVE_UNLINKED", "Student", studentId, institutionId,
                "representativeId=" + representativeId);
    }

    private Representative resolveRepresentative(UUID institutionId, LinkRepresentativeRequest request) {
        boolean hasExisting = request.representativeId() != null;
        boolean hasNew = request.newRepresentative() != null;
        if (hasExisting == hasNew) {
            throw new BadRequestException(
                    "Debe indicar representativeId o newRepresentative, pero no ambos");
        }
        if (hasExisting) {
            return representativeService.find(institutionId, request.representativeId());
        }
        return representativeService.createEntity(institutionId, request.newRepresentative());
    }

    private void applyFlags(StudentRepresentative link, LinkRepresentativeRequest request, UUID studentId) {
        boolean primary = Boolean.TRUE.equals(request.primaryContact());
        link.setPrimaryContact(primary);
        link.setAuthorizedForCommunications(
                request.authorizedForCommunications() == null || request.authorizedForCommunications());
        link.setAuthorizedForBilling(Boolean.TRUE.equals(request.authorizedForBilling()));

        if (primary) {
            linkRepository.findFirstByStudentIdAndPrimaryContactTrue(studentId)
                    .filter(existing -> !existing.getRepresentative().getId()
                            .equals(link.getRepresentative().getId()))
                    .ifPresent(existing -> {
                        existing.setPrimaryContact(false);
                        linkRepository.save(existing);
                    });
        }
    }

    private StudentRepresentativeResponse toResponse(StudentRepresentative link) {
        Representative representative = link.getRepresentative();
        return new StudentRepresentativeResponse(
                link.getId(),
                representative.getId(),
                representative.getFullName(),
                representative.getDocumentNumber(),
                representative.getPhone(),
                representative.getEmail(),
                representative.getRelationship(),
                link.isPrimaryContact(),
                link.isAuthorizedForCommunications(),
                link.isAuthorizedForBilling());
    }
}
