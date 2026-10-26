package com.akira.educacion.communication.repository;

import com.akira.educacion.communication.domain.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    Optional<MessageTemplate> findByIdAndInstitutionId(UUID id, UUID institutionId);

    @Query("""
            select t from MessageTemplate t
            where t.institution.id = :institutionId
              and (:hasActive = false or t.active = :active)
            order by t.name asc
            """)
    List<MessageTemplate> search(@Param("institutionId") UUID institutionId,
            @Param("hasActive") boolean hasActive,
            @Param("active") boolean active);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(UUID institutionId, String name, UUID id);

    boolean existsByInstitutionIdAndCodeIgnoreCase(UUID institutionId, String code);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(UUID institutionId, String code, UUID id);
}
