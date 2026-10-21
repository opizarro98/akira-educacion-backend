package com.akira.educacion.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EducationalLevelRequest(
        @NotBlank(message = "El nombre del nivel es obligatorio") @Size(max = 120) String name,
        @Size(max = 40) String code,
        @Size(max = 255) String description,
        Integer sortOrder,
        Boolean active) {
}
