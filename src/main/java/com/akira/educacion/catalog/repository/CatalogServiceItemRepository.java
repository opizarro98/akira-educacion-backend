package com.akira.educacion.catalog.repository;

import com.akira.educacion.catalog.domain.CatalogServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogServiceItemRepository extends JpaRepository<CatalogServiceItem, UUID> {

    List<CatalogServiceItem> findByInstitutionIdOrderByNameAsc(UUID institutionId);

    Optional<CatalogServiceItem> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(UUID institutionId, String name, UUID id);
}
