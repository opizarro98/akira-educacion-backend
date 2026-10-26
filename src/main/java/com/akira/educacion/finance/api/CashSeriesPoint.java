package com.akira.educacion.finance.api;

import java.math.BigDecimal;

public record CashSeriesPoint(
        String period,
        BigDecimal inflows,
        BigDecimal outflows,
        BigDecimal net) {
}
