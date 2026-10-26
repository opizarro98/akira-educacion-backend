package com.akira.educacion.finance.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.billing.domain.Charge;
import com.akira.educacion.billing.domain.ChargeStatus;
import com.akira.educacion.billing.domain.Payment;
import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.billing.domain.PaymentStatus;
import com.akira.educacion.billing.repository.ChargeRepository;
import com.akira.educacion.billing.repository.PaymentRepository;
import com.akira.educacion.finance.api.CashFlowReportResponse;
import com.akira.educacion.finance.api.CashMovementResponse;
import com.akira.educacion.finance.api.CashSeriesPoint;
import com.akira.educacion.finance.api.CashSummaryReportResponse;
import com.akira.educacion.finance.api.ConfirmedPaymentsReportResponse;
import com.akira.educacion.finance.api.DashboardResponse;
import com.akira.educacion.finance.api.ExpenseCategoryTotal;
import com.akira.educacion.finance.api.ExpenseReportResponse;
import com.akira.educacion.finance.api.OutstandingChargeRow;
import com.akira.educacion.finance.api.OutstandingReportResponse;
import com.akira.educacion.finance.api.PaymentMethodTotal;
import com.akira.educacion.finance.api.PendingPaymentsReportResponse;
import com.akira.educacion.finance.api.ReportExpenseRow;
import com.akira.educacion.finance.api.ReportHeaderResponse;
import com.akira.educacion.finance.api.ReportPaymentRow;
import com.akira.educacion.finance.api.VoidedPaymentsReportResponse;
import com.akira.educacion.finance.domain.CashMovement;
import com.akira.educacion.finance.domain.CashMovementType;
import com.akira.educacion.finance.domain.Expense;
import com.akira.educacion.finance.domain.ExpenseStatus;
import com.akira.educacion.finance.repository.CashMovementRepository;
import com.akira.educacion.finance.repository.ExpenseRepository;
import com.akira.educacion.institution.domain.Institution;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reportes financieros y panel institucional.
 *
 * <p>
 * Todos los importes provienen de documentos confirmados o de movimientos de
 * caja derivados. Las obligaciones pendientes se informan como devengado y
 * nunca se suman al efectivo cobrado. El período se interpreta en la zona
 * horaria de la institución y queda reflejado en la cabecera de cada reporte.
 */
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final ExpenseRepository expenseRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashMovementService cashMovementService;
    private final FinanceSupport support;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Cobros
    // ------------------------------------------------------------------

    @Transactional
    public ConfirmedPaymentsReportResponse confirmedPayments(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        List<Payment> payments = paymentRepository
                .findByInstitutionIdAndStatusAndReceivedDateBetweenOrderByReceivedDateAscCreatedAtAsc(
                        institutionId, PaymentStatus.CONFIRMED, period.fromDate(), period.toDate());

        BigDecimal total = sumPayments(payments);
        List<ReportPaymentRow> rows = payments.stream().map(this::toPaymentRow).toList();
        auditReport(institutionId, "CONFIRMED_PAYMENTS", period);

        return new ConfirmedPaymentsReportResponse(
                header(institution, period, false),
                total,
                payments.size(),
                totalsByMethod(payments),
                rows);
    }

    @Transactional
    public PendingPaymentsReportResponse pendingPayments(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        List<Payment> payments = paymentRepository
                .findByInstitutionIdAndStatusAndReceivedDateBetweenOrderByReceivedDateAscCreatedAtAsc(
                        institutionId, PaymentStatus.PENDING_VERIFICATION, period.fromDate(), period.toDate());
        auditReport(institutionId, "PENDING_PAYMENTS", period);

        return new PendingPaymentsReportResponse(
                header(institution, period, false),
                sumPayments(payments),
                payments.size(),
                payments.stream().map(this::toPaymentRow).toList());
    }

    @Transactional
    public VoidedPaymentsReportResponse voidedPayments(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        // El período del reporte de anulaciones corresponde a la fecha de
        // anulación, no a la fecha en que se recibió el pago.
        List<Payment> payments = paymentRepository
                .findByInstitutionIdAndStatusAndVoidedAtGreaterThanEqualAndVoidedAtLessThanOrderByVoidedAtAsc(
                        institutionId, PaymentStatus.VOID, period.startInclusive(), period.endExclusive());
        auditReport(institutionId, "VOIDED_PAYMENTS", period);

        return new VoidedPaymentsReportResponse(
                header(institution, period, true),
                sumPayments(payments),
                payments.size(),
                payments.stream().map(this::toPaymentRow).toList());
    }

    // ------------------------------------------------------------------
    // Obligaciones
    // ------------------------------------------------------------------

    @Transactional
    public OutstandingReportResponse outstanding(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        LocalDate today = LocalDate.now(period.zone());

        List<Charge> charges = chargeRepository
                .findByInstitutionIdAndStatusNotAndBalanceGreaterThanOrderByDueDateAsc(
                        institutionId, ChargeStatus.VOID, BigDecimal.ZERO);

        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal overdueOutstanding = BigDecimal.ZERO;
        long overdueCount = 0;
        List<OutstandingChargeRow> rows = new ArrayList<>();
        for (Charge charge : charges) {
            BigDecimal balance = support.money(charge.getBalance());
            totalOutstanding = totalOutstanding.add(balance);
            boolean overdue = charge.getDueDate().isBefore(today);
            long daysOverdue = overdue ? ChronoUnit.DAYS.between(charge.getDueDate(), today) : 0;
            if (overdue) {
                overdueOutstanding = overdueOutstanding.add(balance);
                overdueCount++;
            }
            rows.add(new OutstandingChargeRow(
                    charge.getId(),
                    charge.getStudent().fullName(),
                    charge.getIssueDate(),
                    charge.getDueDate(),
                    charge.getCurrency(),
                    charge.getTotal(),
                    charge.getPaidAmount(),
                    balance,
                    overdue,
                    daysOverdue));
        }
        auditReport(institutionId, "OUTSTANDING", period);

        return new OutstandingReportResponse(
                header(institution, period, false),
                support.money(totalOutstanding),
                support.money(overdueOutstanding),
                charges.size(),
                overdueCount,
                rows);
    }

    // ------------------------------------------------------------------
    // Egresos y caja
    // ------------------------------------------------------------------

    @Transactional
    public ExpenseReportResponse expenses(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        List<Expense> expenses = expenseRepository
                .findByInstitutionIdAndStatusAndExpenseDateBetweenOrderByExpenseDateAscCreatedAtAsc(
                        institutionId, ExpenseStatus.CONFIRMED, period.fromDate(), period.toDate());

        BigDecimal total = BigDecimal.ZERO;
        Map<UUID, long[]> counts = new LinkedHashMap<>();
        Map<UUID, BigDecimal> amounts = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        List<ReportExpenseRow> rows = new ArrayList<>();
        for (Expense expense : expenses) {
            total = total.add(expense.getAmount());
            UUID categoryId = expense.getCategory().getId();
            counts.computeIfAbsent(categoryId, key -> new long[1])[0]++;
            amounts.merge(categoryId, expense.getAmount(), BigDecimal::add);
            names.putIfAbsent(categoryId, expense.getCategory().getName());
            rows.add(new ReportExpenseRow(
                    expense.getId(),
                    expense.getCategory().getName(),
                    expense.getExpenseDate(),
                    expense.getDescription(),
                    expense.getMethod(),
                    expense.getBeneficiary(),
                    expense.getAmount(),
                    expense.getCurrency(),
                    expense.getStatus()));
        }

        List<ExpenseCategoryTotal> byCategory = counts.entrySet().stream()
                .map(entry -> new ExpenseCategoryTotal(entry.getKey(), names.get(entry.getKey()),
                        entry.getValue()[0], support.money(amounts.get(entry.getKey()))))
                .toList();
        auditReport(institutionId, "EXPENSES", period);

        return new ExpenseReportResponse(header(institution, period, false), support.money(total),
                expenses.size(), byCategory, rows);
    }

    @Transactional
    public CashFlowReportResponse cashFlow(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        List<CashMovement> movements = cashMovementRepository
                .findByInstitutionIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        institutionId, period.startInclusive(), period.endExclusive());
        auditReport(institutionId, "CASH_FLOW", period);

        BigDecimal inflows = BigDecimal.ZERO;
        BigDecimal outflows = BigDecimal.ZERO;
        List<CashMovementResponse> rows = new ArrayList<>();
        for (CashMovement movement : movements) {
            if (movement.getMovementType().isInflow()) {
                inflows = inflows.add(movement.getAmount());
            } else {
                outflows = outflows.add(movement.getAmount());
            }
            rows.add(cashMovementService.toResponse(movement));
        }

        return new CashFlowReportResponse(header(institution, period, true),
                support.money(inflows), support.money(outflows),
                support.money(inflows.subtract(outflows)), movements.size(), rows);
    }

    @Transactional
    public CashSummaryReportResponse cashSummary(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        BigDecimal inflows = cashMovementRepository.sumByTypeAndPeriod(institutionId,
                CashMovementType.PAYMENT_IN, period.startInclusive(), period.endExclusive())
                .add(cashMovementRepository.sumByTypeAndPeriod(institutionId,
                        CashMovementType.EXPENSE_VOID_REVERSAL, period.startInclusive(), period.endExclusive()));
        BigDecimal outflows = cashMovementRepository.sumByTypeAndPeriod(institutionId,
                CashMovementType.EXPENSE_OUT, period.startInclusive(), period.endExclusive())
                .add(cashMovementRepository.sumByTypeAndPeriod(institutionId,
                        CashMovementType.PAYMENT_VOID_REVERSAL, period.startInclusive(), period.endExclusive()));
        long movementCount = cashMovementRepository
                .countByInstitutionIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        institutionId, period.startInclusive(), period.endExclusive());
        auditReport(institutionId, "CASH_SUMMARY", period);

        return new CashSummaryReportResponse(header(institution, period, true),
                support.money(inflows), support.money(outflows),
                support.money(inflows.subtract(outflows)), movementCount);
    }

    // ------------------------------------------------------------------
    // Dashboard
    // ------------------------------------------------------------------

    @Transactional
    public DashboardResponse dashboard(UUID institutionId, LocalDate from, LocalDate to) {
        Institution institution = support.institution(institutionId);
        ReportPeriod period = ReportPeriod.resolve(institution.getTimezone(), from, to);
        LocalDate today = LocalDate.now(period.zone());

        long activeStudents = count("""
                select count(*) from student where institution_id = ? and status = 'ACTIVE'
                """, institutionId);
        long confirmedEnrollments = count("""
                select count(*) from enrollment where institution_id = ? and status = 'CONFIRMED'
                """, institutionId);

        List<Payment> confirmedPayments = paymentRepository
                .findByInstitutionIdAndStatusAndReceivedDateBetweenOrderByReceivedDateAscCreatedAtAsc(
                        institutionId, PaymentStatus.CONFIRMED, period.fromDate(), period.toDate());
        List<Expense> confirmedExpenses = expenseRepository
                .findByInstitutionIdAndStatusAndExpenseDateBetweenOrderByExpenseDateAscCreatedAtAsc(
                        institutionId, ExpenseStatus.CONFIRMED, period.fromDate(), period.toDate());

        BigDecimal cashInflows = cashMovementRepository.sumByType(institutionId, CashMovementType.PAYMENT_IN)
                .add(cashMovementRepository.sumByType(institutionId, CashMovementType.EXPENSE_VOID_REVERSAL));
        BigDecimal cashOutflows = cashMovementRepository.sumByType(institutionId, CashMovementType.EXPENSE_OUT)
                .add(cashMovementRepository.sumByType(institutionId, CashMovementType.PAYMENT_VOID_REVERSAL));

        auditReport(institutionId, "DASHBOARD", period);

        return new DashboardResponse(
                institutionId,
                institution.getTradeName() == null ? institution.getLegalName() : institution.getTradeName(),
                period.fromDate(),
                period.toDate(),
                period.zone().getId(),
                institution.getCurrency(),
                Instant.now(),
                activeStudents,
                confirmedEnrollments,
                support.money(chargeRepository.sumOutstanding(institutionId, ChargeStatus.VOID)),
                chargeRepository.countOutstanding(institutionId, ChargeStatus.VOID),
                support.money(chargeRepository.sumOverdue(institutionId, ChargeStatus.VOID, today)),
                chargeRepository.countOverdue(institutionId, ChargeStatus.VOID, today),
                sumPayments(confirmedPayments),
                confirmedPayments.size(),
                sumExpenses(confirmedExpenses),
                confirmedExpenses.size(),
                support.money(cashInflows),
                support.money(cashOutflows),
                support.money(cashInflows.subtract(cashOutflows)),
                totalsByMethod(confirmedPayments),
                totalsByCategory(confirmedExpenses),
                cashSeries(institutionId, period));
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private ReportHeaderResponse header(Institution institution, ReportPeriod period, boolean includesVoids) {
        BigDecimal accrued = chargeRepository.sumAccruedByPeriod(institution.getId(), ChargeStatus.VOID,
                period.fromDate(), period.toDate());
        BigDecimal collected = paymentRepository.sumByStatusAndPeriod(institution.getId(),
                PaymentStatus.CONFIRMED, period.fromDate(), period.toDate());
        return new ReportHeaderResponse(
                institution.getId(),
                institution.getTradeName() == null ? institution.getLegalName() : institution.getTradeName(),
                period.fromDate(),
                period.toDate(),
                period.zone().getId(),
                institution.getCurrency(),
                Instant.now(),
                includesVoids,
                support.money(accrued),
                support.money(collected),
                support.money(accrued.subtract(collected)));
    }

    private void auditReport(UUID institutionId, String report, ReportPeriod period) {
        auditService.record("FINANCE_REPORT_VIEWED", "FinanceReport", null, institutionId,
                "report=" + report + "; from=" + period.fromDate() + "; to=" + period.toDate()
                        + "; timezone=" + period.zone().getId());
    }

    private long count(String sql, UUID institutionId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, institutionId);
        return value == null ? 0 : value;
    }

    private BigDecimal sumPayments(List<Payment> payments) {
        return support.money(payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumExpenses(List<Expense> expenses) {
        return support.money(expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private List<PaymentMethodTotal> totalsByMethod(List<Payment> payments) {
        Map<PaymentMethod, long[]> counts = new LinkedHashMap<>();
        Map<PaymentMethod, BigDecimal> amounts = new LinkedHashMap<>();
        for (Payment payment : payments) {
            counts.computeIfAbsent(payment.getMethod(), key -> new long[1])[0]++;
            amounts.merge(payment.getMethod(), payment.getAmount(), BigDecimal::add);
        }
        return counts.entrySet().stream()
                .map(entry -> new PaymentMethodTotal(entry.getKey(), entry.getValue()[0],
                        support.money(amounts.get(entry.getKey()))))
                .toList();
    }

    private List<ExpenseCategoryTotal> totalsByCategory(List<Expense> expenses) {
        Map<UUID, long[]> counts = new LinkedHashMap<>();
        Map<UUID, BigDecimal> amounts = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (Expense expense : expenses) {
            UUID categoryId = expense.getCategory().getId();
            counts.computeIfAbsent(categoryId, key -> new long[1])[0]++;
            amounts.merge(categoryId, expense.getAmount(), BigDecimal::add);
            names.putIfAbsent(categoryId, expense.getCategory().getName());
        }
        return counts.entrySet().stream()
                .map(entry -> new ExpenseCategoryTotal(entry.getKey(), names.get(entry.getKey()),
                        entry.getValue()[0], support.money(amounts.get(entry.getKey()))))
                .toList();
    }

    private ReportPaymentRow toPaymentRow(Payment payment) {
        return new ReportPaymentRow(
                payment.getId(),
                payment.getStudent().fullName(),
                payment.getReceivedDate(),
                payment.getMethod(),
                payment.getReference(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getConfirmedAt(),
                payment.getVoidedAt(),
                payment.getVoidReason());
    }

    /**
     * Serie de caja de los últimos seis meses (incluido el mes final del
     * período), calculada en la zona horaria de la institución.
     */
    private List<CashSeriesPoint> cashSeries(UUID institutionId, ReportPeriod period) {
        ZoneId zone = period.zone();
        YearMonth end = YearMonth.from(period.toDate());
        YearMonth start = end.minusMonths(5);
        Instant startInstant = start.atDay(1).atStartOfDay(zone).toInstant();
        Instant endInstant = end.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        List<CashMovement> movements = cashMovementRepository
                .findByInstitutionIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        institutionId, startInstant, endInstant);

        Map<YearMonth, BigDecimal[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            buckets.put(start.plusMonths(i), new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        }
        for (CashMovement movement : movements) {
            YearMonth month = YearMonth.from(movement.getOccurredAt().atZone(zone));
            BigDecimal[] bucket = buckets.get(month);
            if (bucket == null) {
                continue;
            }
            if (movement.getMovementType().isInflow()) {
                bucket[0] = bucket[0].add(movement.getAmount());
            } else {
                bucket[1] = bucket[1].add(movement.getAmount());
            }
        }
        return buckets.entrySet().stream()
                .map(entry -> new CashSeriesPoint(
                        entry.getKey().toString(),
                        support.money(entry.getValue()[0]),
                        support.money(entry.getValue()[1]),
                        support.money(entry.getValue()[0].subtract(entry.getValue()[1]))))
                .toList();
    }
}
