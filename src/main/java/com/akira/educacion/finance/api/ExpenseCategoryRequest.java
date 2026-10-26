package com.akira.educacion.finance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de una categoría de egreso. El código es opcional y, si se
 * informa, debe ser único dentro de la institución.
 */
public record ExpenseCategoryRequest(
        @Size(max = 40) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String description,
        Boolean active) {
}
