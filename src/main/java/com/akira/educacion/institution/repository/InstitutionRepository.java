package com.akira.educacion.institution.repository;

import com.akira.educacion.institution.domain.Institution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, UUID id);

    @Query("""
            select i from Institution i
            where lower(i.legalName) like lower(concat('%', :search, '%'))
               or lower(coalesce(i.tradeName, '')) like lower(concat('%', :search, '%'))
            """)
    Page<Institution> search(@Param("search") String search, Pageable pageable);
}
