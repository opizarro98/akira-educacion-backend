package com.akira.educacion.finance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidExpenseRequest(@NotBlank @Size(max = 500) String reason) {
}
