package com.akira.educacion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotEmpty Set<String> roles) {
}
