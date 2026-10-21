package com.akira.educacion.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Acceso al usuario autenticado y a su contexto institucional.
 * La institución siempre se toma del token emitido por el backend; nunca de un
 * parámetro enviado por el cliente.
 */
@Component
public class CurrentUser {

    public JwtAuthenticationToken authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication;
        }
        throw new AccessDeniedException("No hay una sesión autenticada");
    }

    public UUID userId() {
        return UUID.fromString(authentication().getToken().getSubject());
    }

    public String email() {
        return authentication().getToken().getClaimAsString("email");
    }

    public String fullName() {
        return authentication().getToken().getClaimAsString("name");
    }

    public UUID institutionIdOrNull() {
        String value = authentication().getToken().getClaimAsString("inst");
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    public UUID requiredInstitutionId() {
        UUID institutionId = institutionIdOrNull();
        if (institutionId == null) {
            throw new AccessDeniedException("La operación requiere una institución");
        }
        return institutionId;
    }

    public Set<String> roles() {
        return authorities().stream()
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> authorities() {
        return authentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isPlatformAdmin() {
        return roles().contains("PLATFORM_ADMIN");
    }
}
