package com.akira.educacion.identity.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.identity.api.CreateUserRequest;
import com.akira.educacion.identity.api.UserResponse;
import com.akira.educacion.identity.domain.AppUser;
import com.akira.educacion.identity.domain.Role;
import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.identity.repository.AppUserRepository;
import com.akira.educacion.identity.repository.RoleRepository;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * Roles que un INSTITUTION_ADMIN puede asignar dentro de su institución.
     */
    private static final Set<String> INSTITUTION_ROLES = Set.of(
            RoleCodes.INSTITUTION_ADMIN,
            RoleCodes.SECRETARY,
            RoleCodes.TREASURY,
            RoleCodes.DIRECTOR);

    private final AppUserRepository appUserRepository;
    private final InstitutionRepository institutionRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional
    public UserResponse createInCurrentInstitution(CreateUserRequest request) {
        UUID institutionId = currentUser.requiredInstitutionId();
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));

        AppUser user = create(institution, request.email(), request.fullName(), request.password(), request.roles());
        return toResponse(user);
    }

    @Transactional
    public AppUser create(Institution institution, String email, String fullName, String rawPassword,
            Set<String> roleCodes) {
        if (institution == null) {
            throw new BadRequestException("El usuario debe pertenecer a una institución");
        }
        validateInstitutionRoles(roleCodes);
        return persist(institution, email, fullName, rawPassword, roleCodes);
    }

    @Transactional
    public AppUser createPlatformAdmin(String email, String fullName, String rawPassword) {
        return persist(null, email, fullName, rawPassword, Set.of(RoleCodes.PLATFORM_ADMIN));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        UUID institutionId = currentUser.requiredInstitutionId();
        Page<AppUser> page = appUserRepository.findByInstitutionId(institutionId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return appUserRepository.existsByEmailIgnoreCase(normalizeEmail(email));
    }

    private AppUser persist(Institution institution, String email, String fullName, String rawPassword,
            Set<String> roleCodes) {
        String normalizedEmail = normalizeEmail(email);
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Ya existe un usuario con el correo indicado");
        }

        List<Role> roles = roleRepository.findByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new BadRequestException("Uno o más roles indicados no existen");
        }

        AppUser user = new AppUser();
        user.setInstitution(institution);
        user.setEmail(normalizedEmail);
        user.setFullName(fullName.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setActive(true);
        user.setRoles(new HashSet<>(roles));
        AppUser saved = appUserRepository.save(user);

        UUID institutionId = institution == null ? null : institution.getId();
        auditService.record("USER_CREATED", "AppUser", saved.getId(), institutionId,
                "email=" + normalizedEmail + "; roles=" + new TreeSet<>(roleCodes));

        return saved;
    }

    private void validateInstitutionRoles(Set<String> roleCodes) {
        for (String code : roleCodes) {
            if (!INSTITUTION_ROLES.contains(code)) {
                throw new BadRequestException("El rol " + code + " no se puede asignar a un usuario de institución");
            }
        }
    }

    private UserResponse toResponse(AppUser user) {
        Set<String> roles = new TreeSet<>();
        user.getRoles().forEach(role -> roles.add(role.getCode()));
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getInstitution() == null ? null : user.getInstitution().getId(),
                roles,
                user.isActive(),
                user.getCreatedAt());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
