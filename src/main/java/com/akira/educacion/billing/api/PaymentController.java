package com.akira.educacion.billing.api;

import com.akira.educacion.billing.domain.PaymentMethod;
import com.akira.educacion.billing.domain.PaymentStatus;
import com.akira.educacion.billing.service.PaymentService;
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
@RequestMapping("/api/v1/billing/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Registro, verificación, aplicación, reversión y anulación de pagos")
public class PaymentController {

    private final PaymentService paymentService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Lista pagos con filtros por estado, estudiante, medio y fecha de recepción")
    public PageResponse<PaymentSummaryResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.list(institutionScope.resolve(institutionId), status, studentId, method,
                receivedFrom, receivedTo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Consulta el detalle de un pago con sus aplicaciones y recibo")
    public PaymentResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return paymentService.get(institutionScope.resolve(institutionId), id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Historial de auditoría del pago")
    public List<PaymentHistoryResponse> history(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return paymentService.history(institutionScope.resolve(institutionId), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un pago en efectivo o una transferencia pendiente de verificación")
    public PaymentResponse register(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody RegisterPaymentRequest request) {
        return paymentService.register(institutionScope.resolve(institutionId), request);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('PAYMENT_VERIFY')")
    @Operation(summary = "Confirma un pago pendiente y emite su recibo interno")
    public PaymentResponse confirm(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return paymentService.confirm(institutionScope.resolve(institutionId), id);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('PAYMENT_VOID')")
    @Operation(summary = "Anula un pago autorizado conservando su trazabilidad")
    public PaymentResponse voidPayment(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody VoidPaymentRequest request) {
        return paymentService.voidPayment(institutionScope.resolve(institutionId), id, request.reason());
    }

    @PostMapping("/{id}/allocations")
    @PreAuthorize("hasAuthority('PAYMENT_APPLY')")
    @Operation(summary = "Aplica un pago confirmado a una o varias obligaciones (idempotente)")
    public PaymentResponse applyAllocations(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ApplyAllocationsRequest request) {
        return paymentService.applyAllocations(institutionScope.resolve(institutionId), id, request);
    }

    @PostMapping("/{id}/allocations/{allocationId}/reverse")
    @PreAuthorize("hasAuthority('PAYMENT_APPLY')")
    @Operation(summary = "Revierte una aplicación y restaura el saldo de la obligación")
    public PaymentResponse reverseAllocation(@PathVariable UUID id, @PathVariable UUID allocationId,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ReverseAllocationRequest request) {
        return paymentService.reverseAllocation(institutionScope.resolve(institutionId), id, allocationId,
                request.reason());
    }
}
