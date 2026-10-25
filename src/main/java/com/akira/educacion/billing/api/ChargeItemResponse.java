package com.akira.educacion.billing.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargeItemResponse(
        UUID id,
        UUID conceptId,
        String conceptCode,
        String conceptName,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal taxableBase,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal total) {
}
