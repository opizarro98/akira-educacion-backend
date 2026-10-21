package com.akira.educacion.students.api;

import com.akira.educacion.students.domain.StudentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos editables de un estudiante. Se envían todos los campos que se desean
 * conservar; los campos opcionales vacíos se guardan como {@code null}.
 */
public record UpdateStudentRequest(
        @NotBlank(message = "Los nombres son obligatorios") @Size(max = 100) String firstName,
        @NotBlank(message = "Los apellidos son obligatorios") @Size(max = 100) String lastName,
        @Size(max = 30) String documentType,
        @Size(max = 40) String documentNumber,
        @Past(message = "La fecha de nacimiento debe ser anterior a hoy") LocalDate birthDate,
        @NotNull(message = "La fecha de ingreso es obligatoria") LocalDate admissionDate,
        @NotNull(message = "El estado es obligatorio") StudentStatus status,
        @Email(message = "El correo no es válido") @Size(max = 150) String email,
        @Size(max = 40) String phone,
        @Size(max = 250) String address,
        @Size(max = 4000) String notes) {
}
