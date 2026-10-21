package com.akira.educacion.identity.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.identity.service.UserService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Usuarios de la institución del usuario autenticado")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Lista los usuarios de la institución")
    public PageResponse<UserResponse> list(
            @PageableDefault(size = 20, sort = "email", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un usuario dentro de la institución")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createInCurrentInstitution(request);
    }
}
