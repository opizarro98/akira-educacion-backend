package com.akira.educacion.common.api;

import java.util.Locale;

/**
 * Utilidades para búsquedas textuales. El patrón se construye en Java para que
 * las consultas no dependan de parámetros opcionales comparados con
 * {@code null}, que PostgreSQL no puede tipar correctamente.
 */
public final class SearchPatterns {

    private SearchPatterns() {
    }

    /**
     * @return {@code %} cuando no hay término (sin filtro), o {@code %termino%}
     *         con los comodines del usuario escapados.
     */
    public static String like(String term) {
        if (term == null || term.isBlank()) {
            return "%";
        }
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
