package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OutstandingChargeRow(
        UUID chargeId,
        String studentName,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        boolean overdue,
        long daysOverdue) {
}
