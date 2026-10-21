package com.akira.educacion.enrollment.service;

import com.akira.educacion.enrollment.domain.Enrollment;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementación por defecto: la generación de obligaciones no forma parte de
 * la Fase 2, por lo que no se genera ningún documento.
 */
@Component
public class NoOpInitialChargeGenerator implements InitialChargeGenerator {

    @Override
    public Optional<UUID> generateFor(Enrollment enrollment) {
        return Optional.empty();
    }
}
