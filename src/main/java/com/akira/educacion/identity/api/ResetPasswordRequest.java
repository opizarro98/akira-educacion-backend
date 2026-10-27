package com.akira.educacion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Restablecimiento administrativo. La contraseña nunca se registra en
 * auditoría ni en logs.
 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 100) String newPassword) {
}
