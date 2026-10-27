package com.akira.educacion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 150) String fullName) {
}
