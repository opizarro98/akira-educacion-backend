package com.akira.educacion.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Datos de un plan. {@code serviceIds} define los servicios incluidos; al
 * enviarse se reemplaza la lista completa.
 */
public record PlanRequest(
        @NotBlank(message = "El nombre del plan es obligatorio") @Size(max = 150) String name,
        @Size(max = 255) String description,
        Boolean active,
        List<UUID> serviceIds) {
}
