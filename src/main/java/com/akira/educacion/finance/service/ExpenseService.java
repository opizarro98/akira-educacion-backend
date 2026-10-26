package com.akira.educacion.finance.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.finance.api.ExpenseHistoryResponse;
import com.akira.educacion.finance.api.ExpenseRequest;
import com.akira.educacion.finance.api.ExpenseResponse;
import com.akira.educacion.finance.api.ExpenseSummaryResponse;
import com.akira.educacion.finance.domain.Expense;
import com.akira.educacion.finance.domain.ExpenseCategory;
import com.akira.educacion.finance.domain.ExpenseHistory;
import com.akira.educacion.finance.domain.ExpenseStatus;
import com.akira.educacion.finance.repository.ExpenseCategoryRepository;
import com.akira.educacion.finance.repository.ExpenseHistoryRepository;
import com.akira.educacion.finance.repository.ExpenseRepository;
import com.akira.educacion.institution.domain.Institution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Registro, confirmación y anulación de egresos.
 *
 * <p>
 * Un borrador es editable. Al confirmarse se registra una salida de caja y el
 * documento deja de ser editable. La anulación conserva el egreso, exige
 * motivo y, si estaba confirmado, genera una reversión de caja. Todas las
 * transiciones se ejecutan en la misma transacción y quedan auditadas.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseHistoryRepository historyRepository;
    private final CashMovementService cashMovementService;
    private final FinanceSupport support;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ExpenseSummaryResponse> list(UUID institutionId, ExpenseStatus status, UUID categoryId,
            LocalDate from, LocalDate to, Pageable pageable) {
        Page<Expense> page = expenseRepository.search(institutionId,
                status != null, status == null ? ExpenseStatus.DRAFT.name() : status.name(),
                categoryId != null, categoryId,
                from != null, from,
                to != null, to,
                pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    @Transactional(readOnly = true)
    public List<ExpenseHistoryResponse> history(UUID institutionId, UUID id) {
        find(institutionId, id);
        return historyRepository.findByInstitutionIdAndExpenseIdOrderByOccurredAtAsc(institutionId, id).stream()
                .map(entry -> new ExpenseHistoryResponse(entry.getId(), entry.getAction(), entry.getDetails(),
                        entry.getReason(), entry.getActorEmail(), entry.getOccurredAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    @Transactional
    public ExpenseResponse create(UUID institutionId, ExpenseRequest request) {
        Institution institution = support.institution(institutionId);
        ExpenseCategory category = findCategory(institutionId, request.categoryId(), true);

        Expense expense = new Expense();
        expense.setInstitution(institution);
        expense.setStatus(ExpenseStatus.DRAFT);
        expense.setResponsibleUserId(support.currentUserIdOrNull());
        expense.setResponsibleEmail(support.actorEmail());
        applyFields(expense, institution, category, request);

        Expense saved = expenseRepository.save(expense);
        recordHistory(saved, "REGISTERED", "amount=" + saved.getAmount() + "; category=" + category.getName(), null);
        auditService.record("EXPENSE_REGISTERED", "Expense", saved.getId(), institutionId,
                "amount=" + saved.getAmount() + "; category=" + category.getName());
        return toResponse(saved);
    }

    /**
     * Solo los borradores se editan. Un egreso confirmado debe anularse y
     * registrarse de nuevo.
     */
    @Transactional
    public ExpenseResponse update(UUID institutionId, UUID id, ExpenseRequest request) {
        Expense expense = lock(institutionId, id);
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new ConflictException("Solo un egreso en borrador puede editarse");
        }
        Institution institution = expense.getInstitution();
        ExpenseCategory category = findCategory(institutionId, request.categoryId(), false);
        applyFields(expense, institution, category, request);

        Expense saved = expenseRepository.save(expense);
        recordHistory(saved, "UPDATED", "amount=" + saved.getAmount() + "; category=" + category.getName(), null);
        auditService.record("EXPENSE_UPDATED", "Expense", saved.getId(), institutionId,
                "amount=" + saved.getAmount());
        return toResponse(saved);
    }

    @Transactional
    public ExpenseResponse confirm(UUID institutionId, UUID id) {
        Expense expense = lock(institutionId, id);
        if (expense.getStatus() == ExpenseStatus.CONFIRMED) {
            throw new ConflictException("El egreso ya está confirmado");
        }
        if (expense.getStatus() == ExpenseStatus.VOID) {
            throw new ConflictException("Un egreso anulado no puede confirmarse");
        }

        expense.setStatus(ExpenseStatus.CONFIRMED);
        expense.setConfirmedAt(Instant.now());
        expense.setConfirmedByEmail(support.actorEmail());
        Expense saved = expenseRepository.saveAndFlush(expense);

        cashMovementService.recordExpenseOut(saved);
        recordHistory(saved, "CONFIRMED", "amount=" + saved.getAmount() + "; method=" + saved.getMethod(), null);
        auditService.record("EXPENSE_CONFIRMED", "Expense", saved.getId(), institutionId,
                "amount=" + saved.getAmount() + "; method=" + saved.getMethod());
        return toResponse(saved);
    }

    /**
     * Anulación auditada. Si el egreso estaba confirmado se genera la reversión
     * de caja en la misma transacción.
     */
    @Transactional
    public ExpenseResponse voidExpense(UUID institutionId, UUID id, String reason) {
        Expense expense = lock(institutionId, id);
        if (expense.getStatus() == ExpenseStatus.VOID) {
            throw new ConflictException("El egreso ya está anulado");
        }
        boolean wasConfirmed = expense.getStatus() == ExpenseStatus.CONFIRMED;
        String trimmedReason = reason.trim();

        expense.setStatus(ExpenseStatus.VOID);
        expense.setVoidReason(trimmedReason);
        expense.setVoidedAt(Instant.now());
        expense.setVoidedBy(support.actorEmail());
        Expense saved = expenseRepository.saveAndFlush(expense);

        if (wasConfirmed) {
            cashMovementService.recordExpenseVoidReversal(saved, trimmedReason);
        }
        recordHistory(saved, "VOIDED", wasConfirmed ? "confirmed expense voided; reversal generated" : null,
                trimmedReason);
        auditService.record("EXPENSE_VOIDED", "Expense", saved.getId(), institutionId,
                "wasConfirmed=" + wasConfirmed + "; reason=" + trimmedReason);
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private void applyFields(Expense expense, Institution institution, ExpenseCategory category,
            ExpenseRequest request) {
        BigDecimal amount = support.money(request.amount());
        if (amount.signum() <= 0) {
            throw new BadRequestException("El importe debe ser mayor que cero");
        }
        String currency = request.currency() == null || request.currency().isBlank()
                ? institution.getCurrency()
                : request.currency().trim().toUpperCase();
        if (!currency.equalsIgnoreCase(institution.getCurrency())) {
            throw new BadRequestException("La moneda del egreso no coincide con la moneda de la institución");
        }

        expense.setCategory(category);
        expense.setAmount(amount);
        expense.setCurrency(institution.getCurrency());
        expense.setExpenseDate(request.expenseDate());
        expense.setDescription(request.description().trim());
        expense.setMethod(request.method());
        expense.setBeneficiary(support.trimToNull(request.beneficiary()));
        expense.setReference(support.trimToNull(request.reference()));
    }

    private Expense find(UUID institutionId, UUID id) {
        return expenseRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El egreso no existe en la institución"));
    }

    private Expense lock(UUID institutionId, UUID id) {
        return expenseRepository.lockByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El egreso no existe en la institución"));
    }

    private ExpenseCategory findCategory(UUID institutionId, UUID categoryId, boolean requireActive) {
        ExpenseCategory category = categoryRepository.findByIdAndInstitutionId(categoryId, institutionId)
                .orElseThrow(() -> new NotFoundException("La categoría de egreso no existe en la institución"));
        if (requireActive && !category.isActive()) {
            throw new BadRequestException("La categoría de egreso está inactiva");
        }
        return category;
    }

    private void recordHistory(Expense expense, String action, String details, String reason) {
        ExpenseHistory history = new ExpenseHistory();
        history.setInstitutionId(expense.getInstitution().getId());
        history.setExpenseId(expense.getId());
        history.setAction(action);
        history.setDetails(details);
        history.setReason(reason);
        history.setActorEmail(support.actorEmail());
        history.setOccurredAt(Instant.now());
        historyRepository.save(history);
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getCategory().getId(),
                expense.getCategory().getName(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getMethod(),
                expense.getBeneficiary(),
                expense.getReference(),
                expense.getStatus(),
                expense.getResponsibleEmail(),
                expense.getConfirmedAt(),
                expense.getConfirmedByEmail(),
                expense.getVoidReason(),
                expense.getVoidedAt(),
                expense.getVoidedBy(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }

    private ExpenseSummaryResponse toSummary(Expense expense) {
        return new ExpenseSummaryResponse(
                expense.getId(),
                expense.getCategory().getName(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getMethod(),
                expense.getBeneficiary(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getStatus(),
                expense.getCreatedAt());
    }
}
