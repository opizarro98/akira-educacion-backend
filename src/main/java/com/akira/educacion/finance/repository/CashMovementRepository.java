package com.akira.educacion.finance.repository;

import com.akira.educacion.finance.domain.CashMovement;
import com.akira.educacion.finance.domain.CashMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    Optional<CashMovement> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByInstitutionIdAndMovementTypeAndPaymentId(UUID institutionId,
            CashMovementType movementType, UUID paymentId);

    boolean existsByInstitutionIdAndMovementTypeAndExpenseId(UUID institutionId,
            CashMovementType movementType, UUID expenseId);

    @Query("""
            select m from CashMovement m
            where m.institution.id = :institutionId
              and (:hasType = false or cast(m.movementType as string) = :type)
              and (:hasFrom = false or m.occurredAt >= :fromInstant)
              and (:hasTo = false or m.occurredAt < :toInstant)
            """)
    Page<CashMovement> search(@Param("institutionId") UUID institutionId,
            @Param("hasType") boolean hasType,
            @Param("type") String type,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromInstant") Instant fromInstant,
            @Param("hasTo") boolean hasTo,
            @Param("toInstant") Instant toInstant,
            Pageable pageable);

    List<CashMovement> findByInstitutionIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            UUID institutionId, Instant fromInstant, Instant toInstant);

    @Query("""
            select coalesce(sum(m.amount), 0) from CashMovement m
            where m.institution.id = :institutionId
              and m.movementType = :type
              and m.occurredAt >= :fromInstant
              and m.occurredAt < :toInstant
            """)
    BigDecimal sumByTypeAndPeriod(@Param("institutionId") UUID institutionId,
            @Param("type") CashMovementType type,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant);

    @Query("""
            select coalesce(sum(m.amount), 0) from CashMovement m
            where m.institution.id = :institutionId
              and m.movementType = :type
            """)
    BigDecimal sumByType(@Param("institutionId") UUID institutionId,
            @Param("type") CashMovementType type);

    long countByInstitutionIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(UUID institutionId,
            Instant fromInstant, Instant toInstant);
}
