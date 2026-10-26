package com.akira.educacion.communication.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailedCommunicationRequest(@NotBlank @Size(max = 500) String reason) {
}
