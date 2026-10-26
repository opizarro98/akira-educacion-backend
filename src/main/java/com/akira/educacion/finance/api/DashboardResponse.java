package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Indicadores del panel institucional. Todas las cifras provienen de
 * operaciones confirmadas y de saldos reales; las obligaciones pendientes se
 * informan separadas del dinero cobrado.
 */
public record DashboardResponse(
        UUID institutionId,
        String institutionName,
        LocalDate fromDate,
        LocalDate toDate,
        String timezone,
        String currency,
        Instant generatedAt,
        long activeStudents,
        long confirmedEnrollments,
        BigDecimal outstandingTotal,
        long outstandingCharges,
        BigDecimal overdueTotal,
        long overdueCharges,
        BigDecimal confirmedPaymentsAmount,
        long confirmedPaymentsCount,
        BigDecimal expensesAmount,
        long expensesCount,
        BigDecimal cashInflows,
        BigDecimal cashOutflows,
        BigDecimal cashBalance,
        List<PaymentMethodTotal> paymentsByMethod,
        List<ExpenseCategoryTotal> expensesByCategory,
        List<CashSeriesPoint> cashSeries) {
}
