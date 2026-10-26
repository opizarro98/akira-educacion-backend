package com.akira.educacion.finance.api;

import com.akira.educacion.finance.service.ExpenseCategoryService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/expense-categories")
@RequiredArgsConstructor
@Tag(name = "Categorías de egreso", description = "Catálogo de categorías para clasificar egresos")
public class ExpenseCategoryController {

    private final ExpenseCategoryService categoryService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_CATEGORY_READ')")
    @Operation(summary = "Lista las categorías de egreso de la institución")
    public List<ExpenseCategoryResponse> list(@RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) Boolean active) {
        return categoryService.list(institutionScope.resolve(institutionId), active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_CATEGORY_READ')")
    @Operation(summary = "Consulta una categoría de egreso")
    public ExpenseCategoryResponse get(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return categoryService.get(institutionScope.resolve(institutionId), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CATEGORY_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una categoría de egreso")
    public ExpenseCategoryResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ExpenseCategoryRequest request) {
        return categoryService.create(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_CATEGORY_WRITE')")
    @Operation(summary = "Actualiza o desactiva una categoría de egreso")
    public ExpenseCategoryResponse update(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ExpenseCategoryRequest request) {
        return categoryService.update(institutionScope.resolve(institutionId), id, request);
    }
}
