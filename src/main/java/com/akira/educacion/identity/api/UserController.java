package com.akira.educacion.identity.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.identity.service.UserService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Administración de usuarios de una institución. La institución se resuelve
 * siempre en el backend: el token para usuarios institucionales y un
 * {@code institutionId} explícito y auditado para PLATFORM_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Administración de usuarios por institución")
public class UserController {

    private final UserService userService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Lista los usuarios de la institución")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "email", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.list(institutionScope.resolve(institutionId), search, pageable);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Lista los roles asignables dentro de una institución")
    public Set<String> assignableRoles(@RequestParam(required = false) UUID institutionId) {
        institutionScope.resolve(institutionId);
        return userService.assignableRoles();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Consulta un usuario de la institución")
    public UserResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return userService.get(institutionScope.resolve(institutionId), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un usuario dentro de la institución")
    public UserResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CreateUserRequest request) {
        return userService.createInInstitution(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Operation(summary = "Edita los datos básicos de un usuario")
    public UserResponse update(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(institutionScope.resolve(institutionId), id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Operation(summary = "Activa o desactiva un usuario")
    public UserResponse updateStatus(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.setActive(institutionScope.resolve(institutionId), id, request.active());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Operation(summary = "Reemplaza los roles de un usuario")
    public UserResponse updateRoles(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        return userService.updateRoles(institutionScope.resolve(institutionId), id, request.roles());
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restablece la contraseña de un usuario (flujo administrativo)")
    public void resetPassword(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(institutionScope.resolve(institutionId), id, request.newPassword());
    }

    @PutMapping("/me/password")
    @Operation(summary = "Cambia la contraseña del usuario autenticado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(request.currentPassword(), request.newPassword());
    }
}
