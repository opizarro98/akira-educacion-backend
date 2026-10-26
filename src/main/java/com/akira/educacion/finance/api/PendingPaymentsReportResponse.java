package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.util.List;

public record PendingPaymentsReportResponse(
        ReportHeaderResponse header,
        BigDecimal totalAmount,
        long paymentCount,
        List<ReportPaymentRow> rows) {
}
