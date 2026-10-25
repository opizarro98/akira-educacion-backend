package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.ChargeStatus;
import com.akira.educacion.billing.service.ChargeService;
import com.akira.educacion.common.api.PageResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/charges")
@RequiredArgsConstructor
@Tag(name = "Obligaciones de cobro", description = "Consulta, creación manual, generación y anulación de obligaciones")
public class ChargeController {

    private final ChargeService chargeService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('CHARGE_READ')")
    @Operation(summary = "Lista obligaciones con filtros por estado, estudiante y fechas")
    public PageResponse<ChargeSummaryResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) ChargeStatus status,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return chargeService.list(institutionScope.resolve(institutionId), status, studentId, issueFrom, issueTo,
                dueFrom, dueTo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CHARGE_READ')")
    @Operation(summary = "Consulta el detalle de una obligación")
    public ChargeResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return chargeService.get(institutionScope.resolve(institutionId), id);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAuthority('CHARGE_READ')")
    @Operation(summary = "Consulta el saldo de una obligación")
    public ChargeBalanceResponse balance(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return chargeService.balance(institutionScope.resolve(institutionId), id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('CHARGE_READ')")
    @Operation(summary = "Historial de cambios de la obligación")
    public List<ChargeHistoryResponse> history(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return chargeService.history(institutionScope.resolve(institutionId), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CHARGE_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una obligación manual")
    public ChargeResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CreateChargeRequest request) {
        return chargeService.createManual(institutionScope.resolve(institutionId), request);
    }

    @PostMapping("/from-enrollment/{enrollmentId}")
    @PreAuthorize("hasAuthority('CHARGE_WRITE')")
    @Operation(summary = "Genera el cobro inicial de una matrícula confirmada (idempotente)")
    public ChargeResponse generateFromEnrollment(@PathVariable UUID enrollmentId,
            @RequestParam(required = false) UUID institutionId,
            @RequestBody(required = false) GenerateInitialChargeRequest request) {
        return chargeService.generateInitial(institutionScope.resolve(institutionId), enrollmentId, request);
    }

    @PostMapping("/generate-monthly")
    @PreAuthorize("hasAuthority('CHARGE_WRITE')")
    @Operation(summary = "Genera una obligación mensual asistida por el usuario (idempotente)")
    public ChargeResponse generateMonthly(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody GenerateMonthlyChargeRequest request) {
        return chargeService.generateMonthly(institutionScope.resolve(institutionId), request);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('CHARGE_WRITE')")
    @Operation(summary = "Anula una obligación indicando el motivo")
    public ChargeResponse voidCharge(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody VoidChargeRequest request) {
        return chargeService.voidCharge(institutionScope.resolve(institutionId), id, request.reason());
    }
}
