package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseReportResponse(
        ReportHeaderResponse header,
        BigDecimal totalAmount,
        long expenseCount,
        List<ExpenseCategoryTotal> byCategory,
        List<ReportExpenseRow> rows) {
}
