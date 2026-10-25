package com.akira.educacion.enrollment.service;

import com.akira.educacion.enrollment.domain.Enrollment;

import java.util.Optional;
import java.util.UUID;

/**
 * Punto de extensión para la generación del cobro inicial de una matrícula.
 *
 * <p>
 * Desde la Fase 3A el cobro inicial se genera explícitamente desde el módulo
 * {@code billing} mediante
 * {@code POST /api/v1/billing/charges/from-enrollment/{enrollmentId}}. La
 * confirmación de la matrícula no emite obligaciones automáticamente, por lo
 * que la implementación por defecto sigue sin hacer nada.
 */
public interface InitialChargeGenerator {

    /**
     * @return identificador del documento de cobro generado, si la
     *         implementación decide generarlo.
     */
    Optional<UUID> generateFor(Enrollment enrollment);
}
