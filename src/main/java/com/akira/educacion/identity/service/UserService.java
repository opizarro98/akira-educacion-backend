package com.akira.educacion.identity.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.api.SearchPatterns;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.identity.api.CreateUserRequest;
import com.akira.educacion.identity.api.UpdateUserRequest;
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
    public UserResponse createInInstitution(UUID institutionId, CreateUserRequest request) {
        Institution institution = loadInstitution(institutionId);
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
    public PageResponse<UserResponse> list(UUID institutionId, String search, Pageable pageable) {
        Page<AppUser> page = appUserRepository.searchByInstitution(institutionId, SearchPatterns.like(search),
                pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID institutionId, UUID userId) {
        return toResponse(find(institutionId, userId));
    }

    @Transactional
    public UserResponse update(UUID institutionId, UUID userId, UpdateUserRequest request) {
        AppUser user = find(institutionId, userId);
        String normalizedEmail = normalizeEmail(request.email());
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail)
                && appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Ya existe un usuario con el correo indicado");
        }

        String previousEmail = user.getEmail();
        String previousFullName = user.getFullName();
        user.setEmail(normalizedEmail);
        user.setFullName(request.fullName().trim());
        AppUser saved = appUserRepository.save(user);

        auditService.record("USER_UPDATED", "AppUser", saved.getId(), institutionId,
                "email=" + previousEmail + "->" + saved.getEmail()
                        + "; fullName=" + previousFullName + "->" + saved.getFullName());
        return toResponse(saved);
    }

    @Transactional
    public UserResponse setActive(UUID institutionId, UUID userId, boolean active) {
        AppUser user = find(institutionId, userId);
        if (!active && user.getId().equals(currentUser.userId())) {
            throw new BadRequestException("No puede desactivar su propio usuario");
        }
        if (user.isActive() == active) {
            return toResponse(user);
        }

        user.setActive(active);
        AppUser saved = appUserRepository.save(user);
        auditService.record(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "AppUser", saved.getId(),
                institutionId, "email=" + saved.getEmail());
        return toResponse(saved);
    }

    @Transactional
    public UserResponse updateRoles(UUID institutionId, UUID userId, Set<String> roleCodes) {
        AppUser user = find(institutionId, userId);
        if (user.getId().equals(currentUser.userId())) {
            throw new BadRequestException("No puede modificar sus propios roles");
        }
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BadRequestException("Debe indicar al menos un rol");
        }
        validateInstitutionRoles(roleCodes);

        List<Role> roles = roleRepository.findByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new BadRequestException("Uno o más roles indicados no existen");
        }

        Set<String> previous = roleCodes(user);
        user.setRoles(new HashSet<>(roles));
        AppUser saved = appUserRepository.save(user);
        auditService.record("USER_ROLES_CHANGED", "AppUser", saved.getId(), institutionId,
                "roles=" + previous + "->" + new TreeSet<>(roleCodes));
        return toResponse(saved);
    }

    @Transactional
    public void resetPassword(UUID institutionId, UUID userId, String newPassword) {
        AppUser user = find(institutionId, userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
        auditService.record("USER_PASSWORD_RESET", "AppUser", user.getId(), institutionId,
                "restablecimiento administrativo; la contraseña no se registra");
    }

    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword) {
        AppUser user = appUserRepository.findById(currentUser.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new NotFoundException("El usuario no existe o está inactivo"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("La nueva contraseña debe ser distinta de la actual");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
        UUID institutionId = user.getInstitution() == null ? null : user.getInstitution().getId();
        auditService.record("USER_PASSWORD_CHANGED", "AppUser", user.getId(), institutionId,
                "cambio propio; la contraseña no se registra");
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return appUserRepository.existsByEmailIgnoreCase(normalizeEmail(email));
    }

    /**
     * @return roles institucionales válidos para asignar desde la administración.
     */
    public Set<String> assignableRoles() {
        return INSTITUTION_ROLES;
    }

    private AppUser find(UUID institutionId, UUID userId) {
        return appUserRepository.findByIdAndInstitutionId(userId, institutionId)
                .orElseThrow(() -> new NotFoundException("El usuario no existe en la institución"));
    }

    private Institution loadInstitution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    private Set<String> roleCodes(AppUser user) {
        Set<String> codes = new TreeSet<>();
        user.getRoles().forEach(role -> codes.add(role.getCode()));
        return codes;
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
