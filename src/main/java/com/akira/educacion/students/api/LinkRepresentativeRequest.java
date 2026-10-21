package com.akira.educacion.students.api;

import com.akira.educacion.representatives.api.CreateRepresentativeRequest;
import jakarta.validation.Valid;

import java.util.UUID;

/**
 * Asocia un representante a un estudiante.
 *
 * <p>
 * Debe indicarse exactamente una de las dos opciones: {@code representativeId}
 * para vincular un representante existente, o {@code newRepresentative} para
 * crearlo y vincularlo en la misma operación.
 *
 * <p>
 * Las responsabilidades son independientes: contacto principal, autorizado para
 * comunicaciones y autorizado para cobro.
 */
public record LinkRepresentativeRequest(
        UUID representativeId,
        @Valid CreateRepresentativeRequest newRepresentative,
        Boolean primaryContact,
        Boolean authorizedForCommunications,
        Boolean authorizedForBilling) {
}
