package com.akira.educacion.billing.api;

import com.akira.educacion.billing.service.BillingSettingsService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/settings")
@RequiredArgsConstructor
@Tag(name = "Configuración de cobro", description = "Frecuencia y fechas de los períodos de cobro")
public class BillingSettingsController {

    private final BillingSettingsService settingsService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('BILLING_SETTINGS_READ')")
    @Operation(summary = "Consulta la configuración de períodos de cobro")
    public BillingSettingsResponse get(@RequestParam(required = false) UUID institutionId) {
        return settingsService.get(institutionScope.resolve(institutionId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('BILLING_SETTINGS_WRITE')")
    @Operation(summary = "Actualiza la configuración de períodos de cobro")
    public BillingSettingsResponse update(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody BillingSettingsRequest request) {
        return settingsService.update(institutionScope.resolve(institutionId), request);
    }
}
