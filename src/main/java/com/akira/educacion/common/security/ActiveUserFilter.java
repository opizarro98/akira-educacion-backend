package com.akira.educacion.common.security;

import com.akira.educacion.identity.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Invalida de inmediato los tokens de usuarios desactivados. El login ya
 * rechaza usuarios inactivos, pero un token emitido antes de la desactivación
 * seguiría siendo válido hasta expirar; este filtro comprueba el estado del
 * usuario en cada petición autenticada.
 */
@Component
@RequiredArgsConstructor
public class ActiveUserFilter extends OncePerRequestFilter {

    private final AppUserRepository appUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String subject = jwtAuthentication.getToken().getSubject();
            if (!isActive(subject)) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("""
                        {"title":"No autenticado","status":401,\
                        "code":"USER_DISABLED",\
                        "detail":"El usuario no existe o está desactivado."}""");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isActive(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }
        try {
            return appUserRepository.existsByIdAndActiveTrue(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
