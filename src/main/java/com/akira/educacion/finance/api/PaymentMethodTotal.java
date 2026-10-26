package com.akira.educacion.finance.api;

import com.akira.educacion.billing.domain.PaymentMethod;

import java.math.BigDecimal;

public record PaymentMethodTotal(
        PaymentMethod method,
        long count,
        BigDecimal amount) {
}
