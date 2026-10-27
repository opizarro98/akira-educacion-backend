package com.akira.educacion.finance.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.finance.domain.ExpenseStatus;
import com.akira.educacion.finance.service.ExpenseService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/expenses")
@RequiredArgsConstructor
@Tag(name = "Egresos", description = "Registro, confirmación y anulación de egresos institucionales")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    @Operation(summary = "Lista egresos con filtros por estado, categoría y fechas")
    public PageResponse<ExpenseSummaryResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return expenseService.list(institutionScope.resolve(institutionId), status, categoryId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    @Operation(summary = "Consulta el detalle de un egreso")
    public ExpenseResponse get(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return expenseService.get(institutionScope.resolve(institutionId), id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    @Operation(summary = "Historial de auditoría del egreso")
    public List<ExpenseHistoryResponse> history(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return expenseService.history(institutionScope.resolve(institutionId), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un egreso en borrador")
    public ExpenseResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_UPDATE')")
    @Operation(summary = "Edita un egreso en borrador")
    public ExpenseResponse update(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(institutionScope.resolve(institutionId), id, request);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('EXPENSE_CONFIRM')")
    @Operation(summary = "Confirma un egreso y genera la salida de caja")
    public ExpenseResponse confirm(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return expenseService.confirm(institutionScope.resolve(institutionId), id);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('EXPENSE_VOID')")
    @Operation(summary = "Anula un egreso; si estaba confirmado genera la reversión de caja")
    public ExpenseResponse voidExpense(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody VoidExpenseRequest request) {
        return expenseService.voidExpense(institutionScope.resolve(institutionId), id, request.reason());
    }
}
