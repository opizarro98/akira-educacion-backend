package com.akira.educacion.finance.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.billing.domain.Payment;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.finance.api.CashMovementResponse;
import com.akira.educacion.finance.domain.CashMovement;
import com.akira.educacion.finance.domain.CashMovementSource;
import com.akira.educacion.finance.domain.CashMovementType;
import com.akira.educacion.finance.domain.Expense;
import com.akira.educacion.finance.repository.CashMovementRepository;
import com.akira.educacion.institution.domain.Institution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Movimientos de caja derivados de pagos y egresos.
 *
 * <p>
 * Ningún movimiento se crea desde la API. Cada método se invoca dentro de la
 * transacción de la operación financiera que lo origina y es idempotente: si el
 * movimiento ya existe para ese documento y tipo, no se duplica. Además, la
 * base de datos impone una clave única por operación.
 */
@Service
@RequiredArgsConstructor
public class CashMovementService {

    private final CashMovementRepository repository;
    private final FinanceSupport support;
    private final AuditService auditService;

    @Transactional
    public void recordPaymentIn(Payment payment) {
        record(payment.getInstitution(), CashMovementType.PAYMENT_IN, CashMovementSource.PAYMENT,
                payment.getId(), payment.getAmount(), payment.getCurrency(),
                payment.getConfirmedAt() == null ? Instant.now() : payment.getConfirmedAt(),
                "Pago confirmado del estudiante " + payment.getStudent().fullName());
    }

    @Transactional
    public void recordPaymentVoidReversal(Payment payment, String reason) {
        record(payment.getInstitution(), CashMovementType.PAYMENT_VOID_REVERSAL, CashMovementSource.PAYMENT,
                payment.getId(), payment.getAmount(), payment.getCurrency(), Instant.now(),
                "Reversión por anulación de pago: " + reason);
    }

    @Transactional
    public void recordExpenseOut(Expense expense) {
        record(expense.getInstitution(), CashMovementType.EXPENSE_OUT, CashMovementSource.EXPENSE,
                expense.getId(), expense.getAmount(), expense.getCurrency(),
                expense.getConfirmedAt() == null ? Instant.now() : expense.getConfirmedAt(),
                "Egreso confirmado: " + expense.getDescription());
    }

    @Transactional
    public void recordExpenseVoidReversal(Expense expense, String reason) {
        record(expense.getInstitution(), CashMovementType.EXPENSE_VOID_REVERSAL, CashMovementSource.EXPENSE,
                expense.getId(), expense.getAmount(), expense.getCurrency(), Instant.now(),
                "Reversión por anulación de egreso: " + reason);
    }

    @Transactional(readOnly = true)
    public PageResponse<CashMovementResponse> list(UUID institutionId, CashMovementType type, LocalDate from,
            LocalDate to, Pageable pageable) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        Page<CashMovement> page = repository.search(institutionId, type != null,
                type == null ? CashMovementType.PAYMENT_IN.name() : type.name(),
                true, period.startInclusive(), true, period.endExclusive(), pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public CashMovementResponse toResponse(CashMovement movement) {
        return new CashMovementResponse(
                movement.getId(),
                movement.getMovementType(),
                movement.getSourceType(),
                movement.sourceId(),
                movement.getAmount(),
                movement.signedAmount(),
                movement.getMovementType().isInflow() ? "IN" : "OUT",
                movement.getCurrency(),
                movement.getOccurredAt(),
                movement.getDescription(),
                movement.getActorEmail(),
                movement.getCreatedAt());
    }

    private void record(Institution institution, CashMovementType type, CashMovementSource sourceType,
            UUID sourceId, java.math.BigDecimal amount, String currency, Instant occurredAt, String description) {
        boolean alreadyRecorded = sourceType == CashMovementSource.PAYMENT
                ? repository.existsByInstitutionIdAndMovementTypeAndPaymentId(institution.getId(), type, sourceId)
                : repository.existsByInstitutionIdAndMovementTypeAndExpenseId(institution.getId(), type, sourceId);
        if (alreadyRecorded) {
            return;
        }

        CashMovement movement = new CashMovement();
        movement.setInstitution(institution);
        movement.setMovementType(type);
        movement.setSourceType(sourceType);
        if (sourceType == CashMovementSource.PAYMENT) {
            movement.setPaymentId(sourceId);
        } else {
            movement.setExpenseId(sourceId);
        }
        movement.setAmount(support.money(amount));
        movement.setCurrency(currency);
        movement.setOccurredAt(occurredAt);
        movement.setDescription(description);
        movement.setActorEmail(support.actorEmail());

        CashMovement saved = repository.saveAndFlush(movement);
        auditService.record("CASH_MOVEMENT_RECORDED", "CashMovement", saved.getId(), institution.getId(),
                "type=" + type + "; source=" + sourceType + ":" + sourceId + "; amount=" + saved.getAmount());
    }
}
