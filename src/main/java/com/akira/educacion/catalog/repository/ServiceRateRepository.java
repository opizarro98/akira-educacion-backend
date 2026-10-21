package com.akira.educacion.catalog.repository;

import com.akira.educacion.catalog.domain.ServiceRate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRateRepository extends JpaRepository<ServiceRate, UUID> {

    @EntityGraph(attributePaths = "service")
    List<ServiceRate> findByInstitutionIdOrderByValidFromDesc(UUID institutionId);

    Optional<ServiceRate> findByIdAndInstitutionId(UUID id, UUID institutionId);
}
