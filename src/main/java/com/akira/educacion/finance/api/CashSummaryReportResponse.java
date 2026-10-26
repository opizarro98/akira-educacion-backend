package com.akira.educacion.finance.api;

import java.math.BigDecimal;

public record CashSummaryReportResponse(
        ReportHeaderResponse header,
        BigDecimal inflows,
        BigDecimal outflows,
        BigDecimal net,
        long movementCount) {
}
