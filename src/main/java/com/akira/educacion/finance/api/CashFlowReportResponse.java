package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowReportResponse(
        ReportHeaderResponse header,
        BigDecimal inflows,
        BigDecimal outflows,
        BigDecimal net,
        long movementCount,
        List<CashMovementResponse> movements) {
}
