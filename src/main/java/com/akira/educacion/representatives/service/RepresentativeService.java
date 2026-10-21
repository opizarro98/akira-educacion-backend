package com.akira.educacion.representatives.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.api.SearchPatterns;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import com.akira.educacion.representatives.api.CreateRepresentativeRequest;
import com.akira.educacion.representatives.api.RepresentativeResponse;
import com.akira.educacion.representatives.api.RepresentativeStudentResponse;
import com.akira.educacion.representatives.api.UpdateRepresentativeRequest;
import com.akira.educacion.representatives.domain.Representative;
import com.akira.educacion.representatives.repository.RepresentativeRepository;
import com.akira.educacion.students.repository.StudentRepresentativeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepresentativeService {

    private final RepresentativeRepository representativeRepository;
    private final StudentRepresentativeRepository linkRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditService auditService;

    @Transactional
    public RepresentativeResponse create(UUID institutionId, CreateRepresentativeRequest request) {
        return toResponse(createEntity(institutionId, request));
    }

    /**
     * Crea el representante y devuelve la entidad administrada. Se usa cuando
     * el representante se crea y vincula en la misma operación.
     */
    @Transactional
    public Representative createEntity(UUID institutionId, CreateRepresentativeRequest request) {
        Institution institution = loadInstitution(institutionId);
        Representative representative = new Representative();
        representative.setInstitution(institution);
        apply(representative, request.fullName(), request.documentNumber(), request.phone(), request.email(),
                request.relationship(), request.notes(), institutionId, null);
        Representative saved = representativeRepository.save(representative);
        auditService.record("REPRESENTATIVE_CREATED", "Representative", saved.getId(), institutionId,
                "fullName=" + saved.getFullName());
        return saved;
    }

    @Transactional
    public Representative update(UUID institutionId, UUID id, UpdateRepresentativeRequest request) {
        Representative representative = find(institutionId, id);
        apply(representative, request.fullName(), request.documentNumber(), request.phone(), request.email(),
                request.relationship(), request.notes(), institutionId, id);
        Representative saved = representativeRepository.save(representative);
        auditService.record("REPRESENTATIVE_UPDATED", "Representative", saved.getId(), institutionId,
                "fullName=" + saved.getFullName());
        return saved;
    }

    @Transactional(readOnly = true)
    public RepresentativeResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<RepresentativeResponse> list(UUID institutionId, String search, Pageable pageable) {
        Page<Representative> page = representativeRepository.search(institutionId,
                SearchPatterns.like(search), pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<RepresentativeStudentResponse> listStudents(UUID institutionId, UUID representativeId) {
        find(institutionId, representativeId);
        return linkRepository.findByRepresentativeIdOrderByStudentLastNameAsc(representativeId).stream()
                .map(link -> new RepresentativeStudentResponse(
                        link.getStudent().getId(),
                        link.getStudent().fullName(),
                        link.getStudent().getDocumentNumber(),
                        link.getStudent().getStatus(),
                        link.isPrimaryContact(),
                        link.isAuthorizedForCommunications(),
                        link.isAuthorizedForBilling()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Representative find(UUID institutionId, UUID id) {
        return representativeRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El representante no existe en la institución"));
    }

    private void apply(Representative representative, String fullName, String documentNumber, String phone,
            String email, String relationship, String notes, UUID institutionId, UUID currentId) {
        String normalizedDocument = normalizeDocument(documentNumber);
        if (normalizedDocument != null) {
            boolean duplicated = currentId == null
                    ? representativeRepository.existsByInstitutionIdAndDocumentNumberIgnoreCase(institutionId,
                            normalizedDocument)
                    : representativeRepository.existsByInstitutionIdAndDocumentNumberIgnoreCaseAndIdNot(institutionId,
                            normalizedDocument, currentId);
            if (duplicated) {
                throw new ConflictException("Ya existe un representante con esa identificación en la institución");
            }
        }
        representative.setFullName(fullName.trim());
        representative.setDocumentNumber(normalizedDocument);
        representative.setPhone(trimToNull(phone));
        representative.setEmail(normalizeEmail(email));
        representative.setRelationship(trimToNull(relationship));
        representative.setNotes(trimToNull(notes));
    }

    private Institution loadInstitution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    public RepresentativeResponse toResponse(Representative representative) {
        return new RepresentativeResponse(
                representative.getId(),
                representative.getFullName(),
                representative.getDocumentNumber(),
                representative.getPhone(),
                representative.getEmail(),
                representative.getRelationship(),
                representative.getNotes(),
                linkRepository.countByRepresentativeId(representative.getId()),
                representative.getCreatedAt(),
                representative.getUpdatedAt());
    }

    private String normalizeDocument(String documentNumber) {
        return documentNumber == null || documentNumber.isBlank()
                ? null
                : documentNumber.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
