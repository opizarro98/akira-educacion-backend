package com.akira.educacion.finance.repository;

import com.akira.educacion.finance.domain.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    Optional<ExpenseCategory> findByIdAndInstitutionId(UUID id, UUID institutionId);

    List<ExpenseCategory> findByInstitutionIdOrderByNameAsc(UUID institutionId);

    @Query("""
            select c from ExpenseCategory c
            where c.institution.id = :institutionId
              and (:hasActive = false or c.active = :active)
            order by c.name asc
            """)
    List<ExpenseCategory> search(@Param("institutionId") UUID institutionId,
            @Param("hasActive") boolean hasActive,
            @Param("active") boolean active);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(UUID institutionId, String name, UUID id);

    boolean existsByInstitutionIdAndCodeIgnoreCase(UUID institutionId, String code);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(UUID institutionId, String code, UUID id);
}
