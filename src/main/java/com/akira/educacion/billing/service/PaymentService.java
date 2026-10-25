package com.akira.educacion.billing.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.billing.api.AllocationItemRequest;
import com.akira.educacion.billing.api.ApplyAllocationsRequest;
import com.akira.educacion.billing.api.PaymentAllocationResponse;
import com.akira.educacion.billing.api.PaymentHistoryResponse;
import com.akira.educacion.billing.api.PaymentResponse;
import com.akira.educacion.billing.api.PaymentSummaryResponse;
import com.akira.educacion.billing.api.ReceiptResponse;
import com.akira.educacion.billing.api.RegisterPaymentRequest;
import com.akira.educacion.billing.domain.Charge;
import com.akira.educacion.billing.domain.ChargeStatus;
import com.akira.educacion.billing.domain.Payment;
import com.akira.educacion.billing.domain.PaymentAllocation;
import com.akira.educacion.billing.domain.PaymentHistory;
import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.billing.domain.PaymentStatus;
import com.akira.educacion.billing.domain.Receipt;
import com.akira.educacion.billing.repository.ChargeRepository;
import com.akira.educacion.billing.repository.PaymentAllocationRepository;
import com.akira.educacion.billing.repository.PaymentHistoryRepository;
import com.akira.educacion.billing.repository.PaymentRepository;
import com.akira.educacion.billing.repository.ReceiptRepository;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.identity.domain.PermissionCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.students.domain.Student;
import com.akira.educacion.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pagos, aplicaciones y reversiones.
 *
 * <p>
 * Todas las operaciones financieras son transaccionales. La obligación y el
 * pago se bloquean con bloqueo pesimista antes de modificar saldos, de modo que
 * dos aplicaciones concurrentes no puedan sobrepasar el importe del pago ni el
 * saldo de la obligación.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int MONEY_SCALE = 2;

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ReceiptRepository receiptRepository;
    private final ChargeRepository chargeRepository;
    private final StudentService studentService;
    private final ReceiptService receiptService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PaymentSummaryResponse> list(UUID institutionId, PaymentStatus status, UUID studentId,
            PaymentMethod method, LocalDate receivedFrom, LocalDate receivedTo, Pageable pageable) {
        Page<Payment> page = paymentRepository.search(institutionId,
                status != null, status == null ? "CONFIRMED" : status.name(),
                studentId != null, studentId,
                method != null, method == null ? "CASH" : method.name(),
                receivedFrom != null, receivedFrom,
                receivedTo != null, receivedTo,
                pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID institutionId, UUID id) {
        Payment payment = paymentRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El pago no existe en la institución"));
        return toDetail(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> history(UUID institutionId, UUID id) {
        find(institutionId, id);
        return paymentHistoryRepository.findByInstitutionIdAndPaymentIdOrderByOccurredAtAsc(institutionId, id)
                .stream()
                .map(entry -> new PaymentHistoryResponse(entry.getId(), entry.getAction(), entry.getDetails(),
                        entry.getReason(), entry.getActorEmail(), entry.getOccurredAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Registro y verificación
    // ------------------------------------------------------------------

    @Transactional
    public PaymentResponse register(UUID institutionId, RegisterPaymentRequest request) {
        String key = trimToNull(request.idempotencyKey());
        if (key != null) {
            Payment existing = paymentRepository.findByInstitutionIdAndIdempotencyKey(institutionId, key)
                    .orElse(null);
            if (existing != null) {
                if (!existing.getStudent().getId().equals(request.studentId())) {
                    throw new ConflictException("La clave de idempotencia ya fue utilizada por otro pago");
                }
                return toDetail(existing);
            }
        }

        BigDecimal amount = money(request.amount());
        if (amount.signum() <= 0) {
            throw new BadRequestException("El importe debe ser mayor que cero");
        }
        if (request.method() == PaymentMethod.BANK_TRANSFER
                && (request.reference() == null || request.reference().isBlank())) {
            throw new BadRequestException("Las transferencias requieren una referencia");
        }

        Student student = studentService.find(institutionId, request.studentId());
        Institution institution = student.getInstitution();
        String currency = institution.getCurrency() == null ? "USD" : institution.getCurrency();
        if (request.currency() != null && !request.currency().equalsIgnoreCase(currency)) {
            throw new BadRequestException("La moneda del pago no coincide con la moneda de la institución");
        }

        Payment payment = new Payment();
        payment.setInstitution(institution);
        payment.setStudent(student);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setMethod(request.method());
        payment.setReference(trimToNull(request.reference()));
        payment.setReceivedDate(request.receivedDate() == null ? LocalDate.now() : request.receivedDate());
        payment.setNotes(trimToNull(request.notes()));
        payment.setIdempotencyKey(key);
        payment.setRegisteredByUserId(currentUserIdOrNull());
        payment.setRegisteredByEmail(actorEmail());
        payment.setAppliedAmount(money(BigDecimal.ZERO));

        boolean cashConfirmed = request.method() == PaymentMethod.CASH && canVerifyPayments();
        payment.setStatus(cashConfirmed ? PaymentStatus.CONFIRMED : PaymentStatus.PENDING_VERIFICATION);
        if (cashConfirmed) {
            payment.setConfirmedByUserId(currentUserIdOrNull());
            payment.setConfirmedByEmail(actorEmail());
            payment.setConfirmedAt(Instant.now());
        }

        Payment saved = paymentRepository.saveAndFlush(payment);
        recordHistory(saved, "REGISTERED", "method=" + saved.getMethod() + "; amount=" + saved.getAmount()
                + "; status=" + saved.getStatus(), null);
        auditService.record("PAYMENT_REGISTERED", "Payment", saved.getId(), institutionId,
                "method=" + saved.getMethod() + "; amount=" + saved.getAmount());

        if (cashConfirmed) {
            receiptService.issue(institution, saved, actorEmail());
            recordHistory(saved, "CONFIRMED", "cash payment confirmed at registration", null);
            auditService.record("PAYMENT_CONFIRMED", "Payment", saved.getId(), institutionId,
                    "method=CASH; amount=" + saved.getAmount());
        }

        return toDetail(saved);
    }

    @Transactional
    public PaymentResponse confirm(UUID institutionId, UUID id) {
        Payment payment = paymentRepository.lockByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El pago no existe en la institución"));
        if (payment.getStatus() == PaymentStatus.VOID) {
            throw new ConflictException("Un pago anulado no puede confirmarse");
        }
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            throw new ConflictException("El pago ya está confirmado");
        }

        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setConfirmedByUserId(currentUserIdOrNull());
        payment.setConfirmedByEmail(actorEmail());
        payment.setConfirmedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        receiptService.issue(payment.getInstitution(), saved, actorEmail());
        recordHistory(saved, "CONFIRMED", "payment verified", null);
        auditService.record("PAYMENT_CONFIRMED", "Payment", saved.getId(), institutionId,
                "amount=" + saved.getAmount());
        return toDetail(saved);
    }

    @Transactional
    public PaymentResponse voidPayment(UUID institutionId, UUID id, String reason) {
        Payment payment = paymentRepository.lockByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El pago no existe en la institución"));
        if (payment.getStatus() == PaymentStatus.VOID) {
            throw new ConflictException("El pago ya está anulado");
        }
        if (payment.getAppliedAmount().signum() > 0) {
            throw new ConflictException("Debe revertir las aplicaciones antes de anular el pago");
        }

        payment.setStatus(PaymentStatus.VOID);
        payment.setVoidReason(reason.trim());
        payment.setVoidedAt(Instant.now());
        payment.setVoidedBy(actorEmail());
        Payment saved = paymentRepository.save(payment);

        receiptRepository.findByInstitutionIdAndPaymentId(institutionId, id)
                .ifPresent(receipt -> receiptService.voidReceipt(receipt, reason.trim()));

        recordHistory(saved, "VOIDED", null, reason.trim());
        auditService.record("PAYMENT_VOIDED", "Payment", saved.getId(), institutionId, "reason=" + reason.trim());
        return toDetail(saved);
    }

    // ------------------------------------------------------------------
    // Aplicaciones
    // ------------------------------------------------------------------

    @Transactional
    public PaymentResponse applyAllocations(UUID institutionId, UUID paymentId, ApplyAllocationsRequest request) {
        String operationKey = trimToNull(request.idempotencyKey());
        Payment payment = paymentRepository.lockByIdAndInstitutionId(paymentId, institutionId)
                .orElseThrow(() -> new NotFoundException("El pago no existe en la institución"));

        // La comprobación se hace con el pago bloqueado para que dos reintentos
        // concurrentes con la misma clave no creen lotes duplicados.
        if (operationKey != null && allocationRepository
                .existsByInstitutionIdAndPaymentIdAndOperationKey(institutionId, paymentId, operationKey)) {
            return toDetail(payment);
        }
        if (payment.getStatus() == PaymentStatus.PENDING_VERIFICATION) {
            throw new ConflictException("Un pago pendiente de verificación no puede aplicarse");
        }
        if (payment.getStatus() == PaymentStatus.VOID) {
            throw new ConflictException("Un pago anulado no puede aplicarse");
        }

        Set<UUID> chargeIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (AllocationItemRequest item : request.allocations()) {
            if (!chargeIds.add(item.chargeId())) {
                throw new BadRequestException("La solicitud contiene la misma obligación más de una vez");
            }
            BigDecimal amount = money(item.amount());
            if (amount.signum() <= 0) {
                throw new BadRequestException("Cada aplicación debe ser mayor que cero");
            }
            total = total.add(amount);
        }
        total = money(total);

        if (total.compareTo(payment.availableAmount()) > 0) {
            throw new ConflictException("La suma de aplicaciones supera el importe disponible del pago");
        }

        for (AllocationItemRequest item : request.allocations()) {
            BigDecimal amount = money(item.amount());
            Charge charge = chargeRepository.lockByIdAndInstitutionId(item.chargeId(), institutionId)
                    .orElseThrow(() -> new NotFoundException("La obligación no existe en la institución"));

            if (!charge.getStudent().getId().equals(payment.getStudent().getId())) {
                throw new BadRequestException("La obligación no corresponde al estudiante del pago");
            }
            if (charge.getStatus() == ChargeStatus.VOID) {
                throw new ConflictException("No se puede aplicar un pago a una obligación anulada");
            }
            if (!charge.getCurrency().equalsIgnoreCase(payment.getCurrency())) {
                throw new BadRequestException("La moneda de la obligación no coincide con la del pago");
            }
            if (amount.compareTo(charge.getBalance()) > 0) {
                throw new ConflictException("La aplicación supera el saldo disponible de la obligación");
            }
            if (allocationRepository.existsByInstitutionIdAndPaymentIdAndChargeIdAndReversedFalse(
                    institutionId, paymentId, charge.getId())) {
                throw new ConflictException("Ya existe una aplicación vigente para esta obligación");
            }

            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setInstitution(charge.getInstitution());
            allocation.setPayment(payment);
            allocation.setCharge(charge);
            allocation.setAmount(amount);
            allocation.setOperationKey(operationKey);
            allocation.setReversed(false);
            allocationRepository.save(allocation);

            charge.setPaidAmount(money(charge.getPaidAmount().add(amount)));
            refreshChargeStatus(charge);
            chargeRepository.save(charge);
        }

        payment.setAppliedAmount(money(payment.getAppliedAmount().add(total)));
        paymentRepository.save(payment);

        recordHistory(payment, "ALLOCATED", "allocations=" + request.allocations().size() + "; total=" + total,
                null);
        auditService.record("PAYMENT_ALLOCATED", "Payment", payment.getId(), institutionId,
                "allocations=" + request.allocations().size() + "; total=" + total);
        return toDetail(payment);
    }

    @Transactional
    public PaymentResponse reverseAllocation(UUID institutionId, UUID paymentId, UUID allocationId, String reason) {
        Payment payment = paymentRepository.lockByIdAndInstitutionId(paymentId, institutionId)
                .orElseThrow(() -> new NotFoundException("El pago no existe en la institución"));
        PaymentAllocation allocation = allocationRepository
                .findByIdAndInstitutionIdAndPaymentId(allocationId, institutionId, paymentId)
                .orElseThrow(() -> new NotFoundException("La aplicación no existe para el pago indicado"));
        if (allocation.isReversed()) {
            throw new ConflictException("La aplicación ya está revertida");
        }

        Charge charge = chargeRepository.lockByIdAndInstitutionId(allocation.getCharge().getId(), institutionId)
                .orElseThrow(() -> new NotFoundException("La obligación no existe en la institución"));
        if (charge.getPaidAmount().compareTo(allocation.getAmount()) < 0) {
            throw new ConflictException("El saldo pagado de la obligación es inconsistente");
        }

        allocation.setReversed(true);
        allocation.setReversedAt(Instant.now());
        allocation.setReversedBy(actorEmail());
        allocation.setReversalReason(reason.trim());
        allocationRepository.save(allocation);

        charge.setPaidAmount(money(charge.getPaidAmount().subtract(allocation.getAmount())));
        refreshChargeStatus(charge);
        chargeRepository.save(charge);

        payment.setAppliedAmount(money(payment.getAppliedAmount().subtract(allocation.getAmount())));
        paymentRepository.save(payment);

        recordHistory(payment, "ALLOCATION_REVERSED",
                "allocationId=" + allocation.getId() + "; chargeId=" + charge.getId() + "; amount="
                        + allocation.getAmount(),
                reason.trim());
        auditService.record("PAYMENT_ALLOCATION_REVERSED", "PaymentAllocation", allocation.getId(), institutionId,
                "paymentId=" + paymentId + "; chargeId=" + charge.getId() + "; amount=" + allocation.getAmount()
                        + "; reason=" + reason.trim());
        return toDetail(payment);
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private Payment find(UUID institutionId, UUID id) {
        return paymentRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El pago no existe en la institución"));
    }

    private void refreshChargeStatus(Charge charge) {
        BigDecimal paid = money(charge.getPaidAmount());
        BigDecimal balance = money(charge.getTotal().subtract(paid));
        if (balance.signum() < 0) {
            throw new ConflictException("El pago excede el total de la obligación");
        }
        charge.setPaidAmount(paid);
        charge.setBalance(balance);
        if (paid.signum() == 0) {
            charge.setStatus(ChargeStatus.PENDING);
        } else if (balance.signum() == 0) {
            charge.setStatus(ChargeStatus.PAID);
        } else {
            charge.setStatus(ChargeStatus.PARTIAL);
        }
    }

    private void recordHistory(Payment payment, String action, String details, String reason) {
        PaymentHistory history = new PaymentHistory();
        history.setInstitutionId(payment.getInstitution().getId());
        history.setPaymentId(payment.getId());
        history.setAction(action);
        history.setDetails(details);
        history.setReason(reason);
        history.setActorEmail(actorEmail());
        history.setOccurredAt(Instant.now());
        paymentHistoryRepository.save(history);
    }

    private boolean canVerifyPayments() {
        try {
            return currentUser.authorities().contains(PermissionCodes.PAYMENT_VERIFY);
        } catch (RuntimeException ex) {
            return false;
        }
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

    private PaymentResponse toDetail(Payment payment) {
        UUID institutionId = payment.getInstitution().getId();
        List<PaymentAllocationResponse> allocations = allocationRepository
                .findByInstitutionIdAndPaymentIdOrderByCreatedAtAsc(institutionId, payment.getId()).stream()
                .map(this::toAllocationResponse)
                .toList();

        ReceiptResponse receipt = receiptRepository.findByInstitutionIdAndPaymentId(institutionId, payment.getId())
                .map(receiptService::toResponse)
                .orElse(null);

        return new PaymentResponse(
                payment.getId(),
                payment.getStudent().getId(),
                payment.getStudent().fullName(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getReference(),
                payment.getReceivedDate(),
                payment.getStatus(),
                payment.getAppliedAmount(),
                availableFor(payment),
                payment.getRegisteredByEmail(),
                payment.getConfirmedByEmail(),
                payment.getConfirmedAt(),
                payment.getNotes(),
                payment.getIdempotencyKey(),
                payment.getVoidReason(),
                payment.getVoidedAt(),
                payment.getVoidedBy(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                allocations,
                receipt);
    }

    private PaymentAllocationResponse toAllocationResponse(PaymentAllocation allocation) {
        return new PaymentAllocationResponse(
                allocation.getId(),
                allocation.getPayment().getId(),
                allocation.getCharge().getId(),
                allocation.getCharge().getDescription(),
                allocation.getCharge().getStatus(),
                allocation.getAmount(),
                allocation.isReversed(),
                allocation.getReversedAt(),
                allocation.getReversedBy(),
                allocation.getReversalReason(),
                allocation.getCreatedAt());
    }

    private PaymentSummaryResponse toSummary(Payment payment) {
        return new PaymentSummaryResponse(
                payment.getId(),
                payment.getStudent().getId(),
                payment.getStudent().fullName(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getReference(),
                payment.getReceivedDate(),
                payment.getStatus(),
                payment.getAppliedAmount(),
                availableFor(payment),
                payment.getCreatedAt());
    }

    /**
     * Solo un pago confirmado representa dinero disponible. Los pagos
     * pendientes de verificación y los anulados se muestran con cero.
     */
    private BigDecimal availableFor(Payment payment) {
        return payment.getStatus() == PaymentStatus.CONFIRMED
                ? money(payment.availableAmount())
                : money(BigDecimal.ZERO);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
