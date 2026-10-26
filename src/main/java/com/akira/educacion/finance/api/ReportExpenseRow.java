package com.akira.educacion.finance.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.finance.domain.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReportExpenseRow(
        UUID id,
        String categoryName,
        LocalDate expenseDate,
        String description,
        PaymentMethod method,
        String beneficiary,
        BigDecimal amount,
        String currency,
        ExpenseStatus status) {
}
