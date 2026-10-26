package com.akira.educacion.finance.api;

import com.akira.educacion.finance.service.FinanceReportService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reportes financieros institucionales. Sin fechas se usa el mes en curso de la
 * institución.
 */
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@Tag(name = "Reportes financieros", description = "Cobros, cartera, egresos, caja y panel institucional")
public class FinanceReportController {

    private final FinanceReportService reportService;
    private final InstitutionScope institutionScope;

    @GetMapping("/reports/confirmed-payments")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Cobros confirmados por período, con totales por medio de pago")
    public ConfirmedPaymentsReportResponse confirmedPayments(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.confirmedPayments(institutionScope.resolve(institutionId), from, to);
    }

    @GetMapping("/reports/pending-payments")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Pagos pendientes de verificación por período")
    public PendingPaymentsReportResponse pendingPayments(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.pendingPayments(institutionScope.resolve(institutionId), from, to);
    }

    @GetMapping("/reports/voided-payments")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Pagos anulados en el período (según fecha de anulación)")
    public VoidedPaymentsReportResponse voidedPayments(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.voidedPayments(institutionScope.resolve(institutionId), from, to);
    }

    @GetMapping("/reports/outstanding")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Obligaciones pendientes y cartera vencida al día de consulta")
    public OutstandingReportResponse outstanding(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.outstanding(institutionScope.resolve(institutionId), from, to);
    }

    @GetMapping("/reports/expenses")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Egresos confirmados por período, con totales por categoría")
    public ExpenseReportResponse expenses(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.expenses(institutionScope.resolve(institutionId), from, to);
    }

    @GetMapping("/reports/cash-flow")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Entradas y salidas de caja del período, con el detalle de movimientos")
    public CashFlowReportResponse cashFlow(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.cashFlow(institutionScope.resolve(institutionId), from, to);
    }

    @GetMapping("/reports/cash-summary")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Resumen de caja por institución y período")
    public CashSummaryReportResponse cashSummary(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.cashSummary(institutionScope.resolve(institutionId), from, to);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('FINANCE_REPORT_READ')")
    @Operation(summary = "Indicadores del panel institucional con datos reales")
    public DashboardResponse dashboard(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.dashboard(institutionScope.resolve(institutionId), from, to);
    }
}
