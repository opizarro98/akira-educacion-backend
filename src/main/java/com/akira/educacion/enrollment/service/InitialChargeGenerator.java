package com.akira.educacion.enrollment.service;

import com.akira.educacion.enrollment.domain.Enrollment;

import java.util.Optional;
import java.util.UUID;

/**
 * Punto de extensión para la generación del cobro inicial de una matrícula.
 *
 * <p>
 * En la Fase 2 no se generan obligaciones ni pagos: las reglas financieras aún
 * no están confirmadas. Esta interfaz delimita el futuro servicio sin inventar
 * comportamiento. La implementación por defecto no hace nada.
 */
public interface InitialChargeGenerator {

    /**
     * @return identificador del documento de cobro generado, si la
     *         implementación decide generarlo.
     */
    Optional<UUID> generateFor(Enrollment enrollment);
}
