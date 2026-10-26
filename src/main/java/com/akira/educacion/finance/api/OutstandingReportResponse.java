package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.util.List;

public record OutstandingReportResponse(
        ReportHeaderResponse header,
        BigDecimal totalOutstanding,
        BigDecimal overdueOutstanding,
        long outstandingCount,
        long overdueCount,
        List<OutstandingChargeRow> rows) {
}
