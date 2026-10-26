package com.akira.educacion.finance.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.finance.domain.CashMovementType;
import com.akira.educacion.finance.service.CashMovementService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/cash-movements")
@RequiredArgsConstructor
@Tag(name = "Movimientos de caja", description = "Entradas, salidas y reversiones derivadas de pagos y egresos")
public class CashMovementController {

    private final CashMovementService cashMovementService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('CASH_MOVEMENT_READ')")
    @Operation(summary = "Lista movimientos de caja; el rango se interpreta en la zona horaria de la institución")
    public PageResponse<CashMovementResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) CashMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return cashMovementService.list(institutionScope.resolve(institutionId), type, from, to, pageable);
    }
}
