package com.akira.educacion.billing.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.billing.api.ChargeBalanceResponse;
import com.akira.educacion.billing.api.ChargeHistoryResponse;
import com.akira.educacion.billing.api.ChargeItemResponse;
import com.akira.educacion.billing.api.ChargeResponse;
import com.akira.educacion.billing.api.ChargeSummaryResponse;
import com.akira.educacion.billing.api.CreateChargeItemRequest;
import com.akira.educacion.billing.api.CreateChargeRequest;
import com.akira.educacion.billing.api.GenerateInitialChargeRequest;
import com.akira.educacion.billing.api.GenerateMonthlyChargeRequest;
import com.akira.educacion.billing.domain.BillingConcept;
import com.akira.educacion.billing.domain.BillingSettings;
import com.akira.educacion.billing.domain.Charge;
import com.akira.educacion.billing.domain.ChargeHistory;
import com.akira.educacion.billing.domain.ChargeItem;
import com.akira.educacion.billing.domain.ChargeOrigin;
import com.akira.educacion.billing.domain.ChargeStatus;
import com.akira.educacion.billing.repository.ChargeHistoryRepository;
import com.akira.educacion.billing.repository.ChargeRepository;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.enrollment.domain.Enrollment;
import com.akira.educacion.enrollment.domain.EnrollmentServiceLine;
import com.akira.educacion.enrollment.domain.EnrollmentStatus;
import com.akira.educacion.enrollment.repository.EnrollmentRepository;
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
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Obligaciones de cobro.
 *
 * <p>
 * Las obligaciones son documentos históricos: se crean, se consultan y se
 * anulan, pero no se editan. Los importes se calculan con {@link BigDecimal} y
 * se redondean a dos decimales; nunca se aceptan valores negativos.
 *
 * <p>
 * La idempotencia se resuelve con claves deterministas y con índices únicos en
 * base de datos, de modo que reintentar una generación no duplica documentos.
 */
@Service
@RequiredArgsConstructor
public class ChargeService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;

    private final ChargeRepository chargeRepository;
    private final ChargeHistoryRepository historyRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BillingConceptService conceptService;
    private final BillingSettingsService settingsService;
    private final StudentService studentService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ChargeSummaryResponse> list(UUID institutionId, ChargeStatus status, UUID studentId,
            LocalDate issueFrom, LocalDate issueTo, LocalDate dueFrom, LocalDate dueTo, Pageable pageable) {
        Page<Charge> page = chargeRepository.search(institutionId,
                status != null, status == null ? "PENDING" : status.name(),
                studentId != null, studentId,
                issueFrom != null, issueFrom,
                issueTo != null, issueTo,
                dueFrom != null, dueFrom,
                dueTo != null, dueTo,
                pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public ChargeResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    @Transactional(readOnly = true)
    public ChargeBalanceResponse balance(UUID institutionId, UUID id) {
        Charge charge = find(institutionId, id);
        return new ChargeBalanceResponse(charge.getId(), charge.getStatus(), charge.getCurrency(),
                charge.getTotal(), charge.getPaidAmount(), charge.getBalance());
    }

    @Transactional(readOnly = true)
    public List<ChargeHistoryResponse> history(UUID institutionId, UUID id) {
        find(institutionId, id);
        return historyRepository.findByInstitutionIdAndChargeIdOrderByOccurredAtAsc(institutionId, id).stream()
                .map(entry -> new ChargeHistoryResponse(entry.getId(), entry.getAction(), entry.getFromStatus(),
                        entry.getToStatus(), entry.getReason(), entry.getDetails(), entry.getActorEmail(),
                        entry.getOccurredAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Creación manual
    // ------------------------------------------------------------------

    @Transactional
    public ChargeResponse createManual(UUID institutionId, CreateChargeRequest request) {
        String key = trimToNull(request.idempotencyKey());
        if (key != null) {
            Charge existing = chargeRepository.findByInstitutionIdAndIdempotencyKey(institutionId, key).orElse(null);
            if (existing != null) {
                if (!existing.getStudent().getId().equals(request.studentId())) {
                    throw new ConflictException("La clave de idempotencia ya fue utilizada por otra obligación");
                }
                return toResponse(existing);
            }
        }

        validateDates(request.issueDate(), request.dueDate());
        Student student = studentService.find(institutionId, request.studentId());
        Enrollment enrollment = resolveEnrollment(institutionId, request.enrollmentId(), student);

        Charge charge = newCharge(institutionId, student, enrollment, ChargeOrigin.MANUAL, key,
                request.issueDate(), request.dueDate(), request.description(), "Obligación de cobro");

        BigDecimal vatRate = currentVatRate(student.getInstitution());
        for (CreateChargeItemRequest itemRequest : request.items()) {
            BillingConcept concept = conceptService.find(institutionId, itemRequest.conceptId());
            addItem(charge, concept, concept.getCode(), concept.getName(),
                    itemRequest.description() == null || itemRequest.description().isBlank()
                            ? concept.getName()
                            : itemRequest.description().trim(),
                    itemRequest.quantity(), money(itemRequest.unitPrice()),
                    itemRequest.discount() == null ? BigDecimal.ZERO : money(itemRequest.discount()),
                    vatRate);
        }

        saveTotals(charge);
        Charge saved = chargeRepository.saveAndFlush(charge);
        recordHistory(saved, "CREATED", null, ChargeStatus.PENDING, null,
                "origin=MANUAL; items=" + saved.getItems().size());
        auditService.record("CHARGE_CREATED", "Charge", saved.getId(), institutionId,
                "origin=MANUAL; total=" + saved.getTotal());
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // Cobro inicial desde matrícula
    // ------------------------------------------------------------------

    @Transactional
    public ChargeResponse generateInitial(UUID institutionId, UUID enrollmentId,
            GenerateInitialChargeRequest request) {
        String key = "ENROLLMENT_INITIAL:" + enrollmentId;
        Charge existing = chargeRepository.findByInstitutionIdAndIdempotencyKey(institutionId, key).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Enrollment enrollment = enrollmentRepository.findByIdAndInstitutionId(enrollmentId, institutionId)
                .orElseThrow(() -> new NotFoundException("La matrícula no existe en la institución"));
        if (enrollment.getStatus() != EnrollmentStatus.CONFIRMED) {
            throw new ConflictException("Solo una matrícula confirmada puede generar el cobro inicial");
        }

        BillingSettings settings = settingsService.effectiveSettings(institutionId);
        LocalDate issueDate = request != null && request.issueDate() != null
                ? request.issueDate()
                : LocalDate.now();
        LocalDate dueDate = request != null && request.dueDate() != null
                ? request.dueDate()
                : issueDate.plusDays(settings.getDueDays());
        validateDates(issueDate, dueDate);

        Charge charge = newCharge(institutionId, enrollment.getStudent(), enrollment, ChargeOrigin.ENROLLMENT, key,
                issueDate, dueDate,
                request == null ? null : request.description(),
                "Cobro inicial de matrícula");

        BigDecimal frozenRate = enrollment.getVatRate() != null
                ? money(enrollment.getVatRate())
                : money(currentVatRate(enrollment.getInstitution()));

        BillingConcept enrollmentConcept = request == null || request.conceptId() == null
                ? null
                : conceptService.find(institutionId, request.conceptId());
        addItem(charge, enrollmentConcept, "MATRICULA", "Matrícula",
                enrollmentConcept == null ? "Matrícula" : enrollmentConcept.getName(),
                1, money(enrollment.getAgreedPrice()), BigDecimal.ZERO, frozenRate);

        for (EnrollmentServiceLine line : enrollment.getServices()) {
            addItem(charge, null, "SERVICIO", line.getService().getName(),
                    line.getService().getName(), line.getQuantity(), money(line.getAgreedPrice()), BigDecimal.ZERO,
                    frozenRate);
        }

        saveTotals(charge);
        Charge saved = chargeRepository.saveAndFlush(charge);
        recordHistory(saved, "CREATED", null, ChargeStatus.PENDING, null,
                "origin=ENROLLMENT; enrollmentId=" + enrollmentId + "; vatRate=" + frozenRate);
        auditService.record("CHARGE_GENERATED", "Charge", saved.getId(), institutionId,
                "origin=ENROLLMENT; enrollmentId=" + enrollmentId + "; total=" + saved.getTotal());
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // Generación mensual asistida
    // ------------------------------------------------------------------

    @Transactional
    public ChargeResponse generateMonthly(UUID institutionId, GenerateMonthlyChargeRequest request) {
        BillingSettings settings = settingsService.effectiveSettings(institutionId);
        if (!settings.isActive()) {
            throw new ConflictException("La configuración de cobro está desactivada para la institución");
        }
        if (settings.getFrequency() == com.akira.educacion.billing.domain.BillingFrequency.ONE_TIME) {
            throw new ConflictException("La institución no tiene configurada una frecuencia de cobro periódica");
        }

        String period = request.period();
        YearMonth yearMonth = YearMonth.parse(period);
        LocalDate issueDate = yearMonth.atDay(Math.min(settings.getIssueDay(), yearMonth.lengthOfMonth()));
        LocalDate dueDate = issueDate.plusDays(settings.getDueDays());

        String key = "MONTHLY:" + request.studentId() + ":" + request.conceptId() + ":" + period;
        Charge existing = chargeRepository.findByInstitutionIdAndIdempotencyKey(institutionId, key).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Student student = studentService.find(institutionId, request.studentId());
        BillingConcept concept = conceptService.find(institutionId, request.conceptId());
        BigDecimal amount = request.amount() == null ? concept.getDefaultAmount() : request.amount();
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El importe no puede ser negativo");
        }

        Charge charge = newCharge(institutionId, student, null, ChargeOrigin.MONTHLY, key, issueDate, dueDate,
                request.description(),
                concept.getName() + " " + period);
        addItem(charge, concept, concept.getCode(), concept.getName(), concept.getName(), 1, money(amount),
                BigDecimal.ZERO, currentVatRate(student.getInstitution()));

        saveTotals(charge);
        Charge saved = chargeRepository.saveAndFlush(charge);
        recordHistory(saved, "CREATED", null, ChargeStatus.PENDING, null,
                "origin=MONTHLY; period=" + period + "; conceptId=" + concept.getId());
        auditService.record("CHARGE_GENERATED", "Charge", saved.getId(), institutionId,
                "origin=MONTHLY; period=" + period + "; total=" + saved.getTotal());
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // Anulación
    // ------------------------------------------------------------------

    @Transactional
    public ChargeResponse voidCharge(UUID institutionId, UUID id, String reason) {
        Charge charge = find(institutionId, id);
        if (charge.getStatus() == ChargeStatus.VOID) {
            throw new ConflictException("La obligación ya está anulada");
        }
        if (charge.getPaidAmount().signum() > 0 || charge.getStatus() == ChargeStatus.PARTIAL
                || charge.getStatus() == ChargeStatus.PAID) {
            throw new ConflictException("No se puede anular una obligación con pagos registrados");
        }

        ChargeStatus previous = charge.getStatus();
        charge.setStatus(ChargeStatus.VOID);
        charge.setBalance(money(BigDecimal.ZERO));
        charge.setVoidReason(reason.trim());
        charge.setVoidedAt(Instant.now());
        charge.setVoidedBy(actorEmail());

        Charge saved = chargeRepository.save(charge);
        recordHistory(saved, "VOIDED", previous, ChargeStatus.VOID, reason.trim(), null);
        auditService.record("CHARGE_VOIDED", "Charge", saved.getId(), institutionId, "reason=" + reason.trim());
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private Charge find(UUID institutionId, UUID id) {
        return chargeRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("La obligación de cobro no existe en la institución"));
    }

    private Enrollment resolveEnrollment(UUID institutionId, UUID enrollmentId, Student student) {
        if (enrollmentId == null) {
            return null;
        }
        Enrollment enrollment = enrollmentRepository.findByIdAndInstitutionId(enrollmentId, institutionId)
                .orElseThrow(() -> new NotFoundException("La matrícula no existe en la institución"));
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new BadRequestException("La matrícula no corresponde al estudiante indicado");
        }
        return enrollment;
    }

    private Charge newCharge(UUID institutionId, Student student, Enrollment enrollment, ChargeOrigin origin,
            String idempotencyKey, LocalDate issueDate, LocalDate dueDate, String description,
            String defaultDescription) {
        Institution institution = student.getInstitution();
        Charge charge = new Charge();
        charge.setInstitution(institution);
        charge.setStudent(student);
        charge.setEnrollment(enrollment);
        charge.setOrigin(origin);
        charge.setIdempotencyKey(idempotencyKey);
        charge.setIssueDate(issueDate);
        charge.setDueDate(dueDate);
        charge.setStatus(ChargeStatus.PENDING);
        charge.setCurrency(institution.getCurrency() == null ? "USD" : institution.getCurrency());
        charge.setDescription(description == null || description.isBlank()
                ? defaultDescription
                : description.trim());
        charge.setResponsibleUserId(currentUserIdOrNull());
        charge.setResponsibleEmail(actorEmail());
        charge.setPaidAmount(money(BigDecimal.ZERO));
        return charge;
    }

    private void addItem(Charge charge, BillingConcept concept, String conceptCode, String conceptName,
            String description, int quantity, BigDecimal unitPrice, BigDecimal discount, BigDecimal vatRate) {
        if (quantity <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor que cero");
        }
        if (unitPrice.signum() < 0 || discount.signum() < 0) {
            throw new BadRequestException("Los importes no pueden ser negativos");
        }
        if (vatRate.signum() < 0) {
            throw new BadRequestException("La tasa de IVA no puede ser negativa");
        }

        BigDecimal gross = money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        if (discount.compareTo(gross) > 0) {
            throw new BadRequestException("El descuento no puede superar el importe de la línea");
        }
        BigDecimal taxableBase = money(gross.subtract(discount));
        BigDecimal vatAmount = money(taxableBase.multiply(vatRate).divide(ONE_HUNDRED, MONEY_SCALE,
                RoundingMode.HALF_UP));
        BigDecimal total = money(taxableBase.add(vatAmount));

        ChargeItem item = new ChargeItem();
        item.setConcept(concept);
        item.setConceptCode(conceptCode);
        item.setConceptName(conceptName);
        item.setDescription(trimToNull(description));
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setDiscount(discount);
        item.setTaxableBase(taxableBase);
        item.setVatRate(vatRate);
        item.setVatAmount(vatAmount);
        item.setTotal(total);
        charge.addItem(item);
    }

    private void saveTotals(Charge charge) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (ChargeItem item : charge.getItems()) {
            subtotal = subtotal.add(item.getTaxableBase());
            tax = tax.add(item.getVatAmount());
        }
        subtotal = money(subtotal);
        tax = money(tax);
        BigDecimal total = money(subtotal.add(tax));
        charge.setSubtotal(subtotal);
        charge.setTaxAmount(tax);
        charge.setTotal(total);
        charge.setBalance(total.subtract(charge.getPaidAmount() == null ? BigDecimal.ZERO : charge.getPaidAmount()));
    }

    /**
     * Tasa vigente de la institución. Si el IVA está deshabilitado o su
     * vigencia es futura, la tasa aplicable es cero. Las obligaciones ya
     * emitidas conservan su propia tasa en el detalle.
     */
    private BigDecimal currentVatRate(Institution institution) {
        if (!institution.isVatEnabled() || institution.getVatRate() == null) {
            return money(BigDecimal.ZERO);
        }
        LocalDate effectiveFrom = institution.getVatEffectiveFrom();
        if (effectiveFrom != null && effectiveFrom.isAfter(LocalDate.now())) {
            return money(BigDecimal.ZERO);
        }
        return money(institution.getVatRate());
    }

    private void validateDates(LocalDate issueDate, LocalDate dueDate) {
        if (issueDate == null || dueDate == null) {
            throw new BadRequestException("Las fechas de emisión y vencimiento son obligatorias");
        }
        if (dueDate.isBefore(issueDate)) {
            throw new BadRequestException("La fecha de vencimiento no puede ser anterior a la de emisión");
        }
    }

    private void recordHistory(Charge charge, String action, ChargeStatus from, ChargeStatus to, String reason,
            String details) {
        ChargeHistory history = new ChargeHistory();
        history.setInstitutionId(charge.getInstitution().getId());
        history.setChargeId(charge.getId());
        history.setAction(action);
        history.setFromStatus(from == null ? null : from.name());
        history.setToStatus(to == null ? null : to.name());
        history.setReason(reason);
        history.setDetails(details);
        history.setActorEmail(actorEmail());
        history.setOccurredAt(Instant.now());
        historyRepository.save(history);
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

    private ChargeResponse toResponse(Charge charge) {
        List<ChargeItemResponse> items = charge.getItems().stream()
                .map(item -> new ChargeItemResponse(item.getId(),
                        item.getConcept() == null ? null : item.getConcept().getId(),
                        item.getConceptCode(), item.getConceptName(), item.getDescription(), item.getQuantity(),
                        item.getUnitPrice(), item.getDiscount(), item.getTaxableBase(), item.getVatRate(),
                        item.getVatAmount(), item.getTotal()))
                .toList();

        return new ChargeResponse(
                charge.getId(),
                charge.getStudent().getId(),
                charge.getStudent().fullName(),
                charge.getEnrollment() == null ? null : charge.getEnrollment().getId(),
                charge.getOrigin(),
                charge.getIdempotencyKey(),
                charge.getStatus(),
                charge.getCurrency(),
                charge.getDescription(),
                charge.getIssueDate(),
                charge.getDueDate(),
                charge.getSubtotal(),
                charge.getTaxAmount(),
                charge.getTotal(),
                charge.getPaidAmount(),
                charge.getBalance(),
                charge.getResponsibleUserId(),
                charge.getResponsibleEmail(),
                charge.getVoidReason(),
                charge.getVoidedAt(),
                charge.getVoidedBy(),
                charge.getCreatedAt(),
                charge.getUpdatedAt(),
                items);
    }

    private ChargeSummaryResponse toSummary(Charge charge) {
        return new ChargeSummaryResponse(
                charge.getId(),
                charge.getStudent().getId(),
                charge.getStudent().fullName(),
                charge.getEnrollment() == null ? null : charge.getEnrollment().getId(),
                charge.getOrigin(),
                charge.getStatus(),
                charge.getCurrency(),
                charge.getDescription(),
                charge.getIssueDate(),
                charge.getDueDate(),
                charge.getSubtotal(),
                charge.getTaxAmount(),
                charge.getTotal(),
                charge.getPaidAmount(),
                charge.getBalance(),
                charge.getCreatedAt());
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
