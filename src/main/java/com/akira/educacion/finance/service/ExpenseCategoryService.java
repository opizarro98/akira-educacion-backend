package com.akira.educacion.finance.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.finance.api.ExpenseCategoryRequest;
import com.akira.educacion.finance.api.ExpenseCategoryResponse;
import com.akira.educacion.finance.domain.ExpenseCategory;
import com.akira.educacion.finance.repository.ExpenseCategoryRepository;
import com.akira.educacion.institution.domain.Institution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo de categorías de egreso por institución. Las categorías no se
 * eliminan: se desactivan para preservar el histórico de egresos.
 */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository repository;
    private final FinanceSupport support;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> list(UUID institutionId, Boolean active) {
        return repository.search(institutionId, active != null, active != null && active).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseCategoryResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    @Transactional
    public ExpenseCategoryResponse create(UUID institutionId, ExpenseCategoryRequest request) {
        Institution institution = support.institution(institutionId);
        String name = request.name().trim();
        String code = support.trimToNull(request.code());
        if (repository.existsByInstitutionIdAndNameIgnoreCase(institutionId, name)) {
            throw new ConflictException("Ya existe una categoría con ese nombre");
        }
        if (code != null && repository.existsByInstitutionIdAndCodeIgnoreCase(institutionId, code)) {
            throw new ConflictException("Ya existe una categoría con ese código");
        }

        ExpenseCategory category = new ExpenseCategory();
        category.setInstitution(institution);
        category.setName(name);
        category.setCode(code);
        category.setDescription(support.trimToNull(request.description()));
        category.setActive(request.active() == null || request.active());
        ExpenseCategory saved = repository.save(category);
        auditService.record("EXPENSE_CATEGORY_CREATED", "ExpenseCategory", saved.getId(), institutionId,
                "name=" + name);
        return toResponse(saved);
    }

    @Transactional
    public ExpenseCategoryResponse update(UUID institutionId, UUID id, ExpenseCategoryRequest request) {
        ExpenseCategory category = find(institutionId, id);
        String name = request.name().trim();
        String code = support.trimToNull(request.code());
        if (repository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(institutionId, name, id)) {
            throw new ConflictException("Ya existe una categoría con ese nombre");
        }
        if (code != null && repository.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(institutionId, code, id)) {
            throw new ConflictException("Ya existe una categoría con ese código");
        }

        category.setName(name);
        category.setCode(code);
        category.setDescription(support.trimToNull(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
        ExpenseCategory saved = repository.save(category);
        auditService.record("EXPENSE_CATEGORY_UPDATED", "ExpenseCategory", saved.getId(), institutionId,
                "name=" + name);
        return toResponse(saved);
    }

    private ExpenseCategory find(UUID institutionId, UUID id) {
        return repository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("La categoría de egreso no existe en la institución"));
    }

    public ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return new ExpenseCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
