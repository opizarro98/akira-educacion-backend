package com.akira.educacion.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogServiceRequest(
        @NotBlank(message = "El nombre del servicio es obligatorio") @Size(max = 150) String name,
        @Size(max = 40) String code,
        @Size(max = 255) String description,
        Boolean active) {
}
