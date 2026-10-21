package com.akira.educacion.institution.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resuelve la institución sobre la que opera una petición.
 *
 * <p>
 * Para usuarios institucionales la institución proviene siempre del token; un
 * {@code institutionId} enviado por el cliente solo se acepta si coincide con
 * la
 * propia institución y nunca se usa como mecanismo de autorización.
 *
 * <p>
 * {@code PLATFORM_ADMIN} no pertenece a una institución: para operar sobre
 * datos
 * institucionales debe indicar el {@code institutionId} de forma explícita.
 * Cada
 * acceso global queda registrado en la auditoría. La resolución ocurre en el
 * controlador, fuera de la transacción del servicio, para que el evento de
 * auditoría se confirme aunque la consulta sea de solo lectura.
 */
@Component
@RequiredArgsConstructor
public class InstitutionScope {

    private final CurrentUser currentUser;
    private final InstitutionRepository institutionRepository;
    private final AuditService auditService;

    /**
     * @param explicitInstitutionId valor enviado por el cliente; opcional para
     *                              usuarios institucionales, obligatorio para
     *                              PLATFORM_ADMIN.
     */
    public UUID resolve(UUID explicitInstitutionId) {
        if (currentUser.isPlatformAdmin()) {
            if (explicitInstitutionId == null) {
                throw new BadRequestException(
                        "El administrador de plataforma debe indicar institutionId de forma explícita");
            }
            if (!institutionRepository.existsById(explicitInstitutionId)) {
                throw new NotFoundException("La institución indicada no existe");
            }
            auditService.record("PLATFORM_GLOBAL_ACCESS", "Institution", explicitInstitutionId,
                    explicitInstitutionId, "operacion global con alcance explicito");
            return explicitInstitutionId;
        }

        UUID own = currentUser.requiredInstitutionId();
        if (explicitInstitutionId != null && !explicitInstitutionId.equals(own)) {
            throw new AccessDeniedException("No puede operar sobre datos de otra institución");
        }
        return own;
    }
}
