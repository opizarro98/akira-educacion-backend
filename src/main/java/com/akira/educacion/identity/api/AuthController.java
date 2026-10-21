package com.akira.educacion.identity.api;

import com.akira.educacion.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión y perfil del usuario autenticado")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve un token JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Devuelve el perfil del usuario autenticado")
    public UserProfile me() {
        return authService.currentProfile();
    }
}
