package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.util.List;

public record ConfirmedPaymentsReportResponse(
        ReportHeaderResponse header,
        BigDecimal totalAmount,
        long paymentCount,
        List<PaymentMethodTotal> byMethod,
        List<ReportPaymentRow> rows) {
}
