package com.akira.educacion.representatives.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de un representante.
 */
public record UpdateRepresentativeRequest(
        @NotBlank(message = "El nombre completo es obligatorio") @Size(max = 200) String fullName,
        @Size(max = 40) String documentNumber,
        @Size(max = 40) String phone,
        @Email(message = "El correo no es válido") @Size(max = 150) String email,
        @Size(max = 60) String relationship,
        @Size(max = 4000) String notes) {
}
