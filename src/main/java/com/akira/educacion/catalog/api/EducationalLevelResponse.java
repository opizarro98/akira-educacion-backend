package com.akira.educacion.catalog.api;

import java.util.UUID;

public record EducationalLevelResponse(
        UUID id,
        String name,
        String code,
        String description,
        int sortOrder,
        boolean active) {
}
