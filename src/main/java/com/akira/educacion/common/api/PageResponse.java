package com.akira.educacion.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Respuesta paginada estable para la API, independiente de la representación
 * interna de Spring Data.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
