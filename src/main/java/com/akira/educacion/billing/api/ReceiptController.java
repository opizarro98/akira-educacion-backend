package com.akira.educacion.billing.api;

import com.akira.educacion.billing.service.ReceiptService;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/receipts")
@RequiredArgsConstructor
@Tag(name = "Recibos internos", description = "Recibos emitidos al confirmar pagos")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('RECEIPT_READ')")
    @Operation(summary = "Lista recibos internos con filtros por pago o estudiante")
    public PageResponse<ReceiptResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) UUID studentId,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return receiptService.list(institutionScope.resolve(institutionId), paymentId, studentId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RECEIPT_READ')")
    @Operation(summary = "Consulta un recibo interno")
    public ReceiptResponse get(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return receiptService.get(institutionScope.resolve(institutionId), id);
    }

    @GetMapping("/by-payment/{paymentId}")
    @PreAuthorize("hasAuthority('RECEIPT_READ')")
    @Operation(summary = "Consulta el recibo de un pago")
    public ReceiptResponse getByPayment(@PathVariable UUID paymentId,
            @RequestParam(required = false) UUID institutionId) {
        return receiptService.getByPayment(institutionScope.resolve(institutionId), paymentId);
    }
}
