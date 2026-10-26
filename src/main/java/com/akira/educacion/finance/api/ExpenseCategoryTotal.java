package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseCategoryTotal(
        UUID categoryId,
        String categoryName,
        long count,
        BigDecimal amount) {
}
