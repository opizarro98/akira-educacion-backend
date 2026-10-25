package com.akira.educacion.billing.api;

import com.akira.educacion.billing.service.BillingConceptService;
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
@RequestMapping("/api/v1/billing/concepts")
@RequiredArgsConstructor
@Tag(name = "Conceptos de cobro", description = "Catálogo institucional de conceptos financieros")
public class BillingConceptController {

    private final BillingConceptService conceptService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('BILLING_CONCEPT_READ')")
    @Operation(summary = "Lista los conceptos de cobro de la institución")
    public List<BillingConceptResponse> list(@RequestParam(required = false) UUID institutionId) {
        return conceptService.list(institutionScope.resolve(institutionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BILLING_CONCEPT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un concepto de cobro")
    public BillingConceptResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody BillingConceptRequest request) {
        return conceptService.create(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BILLING_CONCEPT_WRITE')")
    @Operation(summary = "Actualiza un concepto de cobro")
    public BillingConceptResponse update(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody BillingConceptRequest request) {
        return conceptService.update(institutionScope.resolve(institutionId), id, request);
    }
}
