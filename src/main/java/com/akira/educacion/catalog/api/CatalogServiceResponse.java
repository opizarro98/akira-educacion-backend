package com.akira.educacion.catalog.api;

import java.util.UUID;

public record CatalogServiceResponse(
        UUID id,
        String name,
        String code,
        String description,
        boolean active) {
}
