package com.akira.educacion.communication.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plantilla de mensaje. Los marcadores permitidos son
 * {@code {{estudiante}}}, {@code {{representante}}}, {@code {{institucion}}},
 * {@code {{fecha}}} y {@code {{detalle_financiero}}}.
 */
public record MessageTemplateRequest(
        @Size(max = 40) String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 4000) String body,
        Boolean active) {
}
