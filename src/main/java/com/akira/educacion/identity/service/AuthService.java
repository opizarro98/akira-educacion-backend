package com.akira.educacion.identity.service;

import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.common.security.JwtService;
import com.akira.educacion.identity.api.LoginRequest;
import com.akira.educacion.identity.api.LoginResponse;
import com.akira.educacion.identity.api.UserProfile;
import com.akira.educacion.identity.domain.AppUser;
import com.akira.educacion.identity.domain.Permission;
import com.akira.educacion.identity.domain.Role;
import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.identity.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "El correo o la contraseña no son correctos.";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = appUserRepository.findWithRolesByEmail(email)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(), toProfile(user));
    }

    @Transactional(readOnly = true)
    public UserProfile currentProfile() {
        UUID userId = currentUser.userId();
        AppUser user = appUserRepository.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new NotFoundException("El usuario no existe o está inactivo"));
        return toProfile(user);
    }

    private UserProfile toProfile(AppUser user) {
        Set<String> roles = new TreeSet<>();
        Set<String> permissions = new TreeSet<>();
        for (Role role : user.getRoles()) {
            roles.add(role.getCode());
            for (Permission permission : role.getPermissions()) {
                permissions.add(permission.getCode());
            }
        }
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getInstitution() == null ? null : user.getInstitution().getId(),
                user.getInstitution() == null ? null : user.getInstitution().getLegalName(),
                roles,
                permissions,
                roles.contains(RoleCodes.PLATFORM_ADMIN));
    }
}
