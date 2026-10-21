package com.akira.educacion.catalog.api;

import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        List<ServiceRef> services) {

    public record ServiceRef(UUID id, String name, String code) {
    }
}
