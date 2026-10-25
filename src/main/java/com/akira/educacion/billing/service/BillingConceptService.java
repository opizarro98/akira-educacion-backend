package com.akira.educacion.billing.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.billing.api.BillingConceptRequest;
import com.akira.educacion.billing.api.BillingConceptResponse;
import com.akira.educacion.billing.domain.BillingConcept;
import com.akira.educacion.billing.repository.BillingConceptRepository;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo de conceptos de cobro. Los conceptos solo aportan una referencia y
 * un importe sugerido: cada obligación guarda su propia instantánea, por lo
 * que modificarlos no altera documentos históricos.
 */
@Service
@RequiredArgsConstructor
public class BillingConceptService {

    private final BillingConceptRepository conceptRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BillingConceptResponse> list(UUID institutionId) {
        return conceptRepository.findByInstitutionIdOrderByNameAsc(institutionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BillingConceptResponse create(UUID institutionId, BillingConceptRequest request) {
        Institution institution = loadInstitution(institutionId);
        validateName(institutionId, request.name(), null);
        String code = normalizeCode(request.code());
        validateCode(institutionId, code, null);

        BillingConcept concept = new BillingConcept();
        concept.setInstitution(institution);
        apply(concept, request, code);
        BillingConcept saved = conceptRepository.save(concept);
        auditService.record("BILLING_CONCEPT_CREATED", "BillingConcept", saved.getId(), institutionId,
                "name=" + saved.getName() + "; defaultAmount=" + saved.getDefaultAmount());
        return toResponse(saved);
    }

    @Transactional
    public BillingConceptResponse update(UUID institutionId, UUID id, BillingConceptRequest request) {
        BillingConcept concept = find(institutionId, id);
        validateName(institutionId, request.name(), id);
        String code = normalizeCode(request.code());
        validateCode(institutionId, code, id);

        apply(concept, request, code);
        BillingConcept saved = conceptRepository.save(concept);
        auditService.record("BILLING_CONCEPT_UPDATED", "BillingConcept", saved.getId(), institutionId,
                "name=" + saved.getName() + "; defaultAmount=" + saved.getDefaultAmount());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BillingConcept find(UUID institutionId, UUID id) {
        return conceptRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El concepto de cobro no existe en la institución"));
    }

    private void apply(BillingConcept concept, BillingConceptRequest request, String code) {
        concept.setCode(code);
        concept.setName(request.name().trim());
        concept.setDescription(trimToNull(request.description()));
        concept.setDefaultAmount(request.defaultAmount() == null
                ? BigDecimal.ZERO
                : request.defaultAmount().setScale(2, RoundingMode.HALF_UP));
        concept.setActive(request.active() == null || request.active());
    }

    private void validateName(UUID institutionId, String name, UUID currentId) {
        boolean duplicated = currentId == null
                ? conceptRepository.existsByInstitutionIdAndNameIgnoreCase(institutionId, name.trim())
                : conceptRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(institutionId, name.trim(),
                        currentId);
        if (duplicated) {
            throw new ConflictException("Ya existe un concepto de cobro con ese nombre");
        }
    }

    private void validateCode(UUID institutionId, String code, UUID currentId) {
        if (code == null) {
            return;
        }
        boolean duplicated = currentId == null
                ? conceptRepository.existsByInstitutionIdAndCodeIgnoreCase(institutionId, code)
                : conceptRepository.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(institutionId, code, currentId);
        if (duplicated) {
            throw new ConflictException("Ya existe un concepto de cobro con ese código");
        }
    }

    private Institution loadInstitution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    private BillingConceptResponse toResponse(BillingConcept concept) {
        return new BillingConceptResponse(concept.getId(), concept.getCode(), concept.getName(),
                concept.getDescription(), concept.getDefaultAmount(), concept.isActive(), concept.getCreatedAt(),
                concept.getUpdatedAt());
    }

    private String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
