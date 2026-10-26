package com.akira.educacion.finance.repository;

import com.akira.educacion.finance.domain.Expense;
import com.akira.educacion.finance.domain.ExpenseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @EntityGraph(attributePaths = { "category" })
    Optional<Expense> findByIdAndInstitutionId(UUID id, UUID institutionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.id = :id and e.institution.id = :institutionId")
    Optional<Expense> lockByIdAndInstitutionId(@Param("id") UUID id,
            @Param("institutionId") UUID institutionId);

    @EntityGraph(attributePaths = { "category" })
    @Query("""
            select e from Expense e
            where e.institution.id = :institutionId
              and (:hasStatus = false or cast(e.status as string) = :status)
              and (:hasCategory = false or e.category.id = :categoryId)
              and (:hasFrom = false or e.expenseDate >= :fromDate)
              and (:hasTo = false or e.expenseDate <= :toDate)
            """)
    Page<Expense> search(@Param("institutionId") UUID institutionId,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") String status,
            @Param("hasCategory") boolean hasCategory,
            @Param("categoryId") UUID categoryId,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasTo") boolean hasTo,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @EntityGraph(attributePaths = { "category" })
    List<Expense> findByInstitutionIdAndStatusAndExpenseDateBetweenOrderByExpenseDateAscCreatedAtAsc(
            UUID institutionId, ExpenseStatus status, LocalDate fromDate, LocalDate toDate);

    long countByInstitutionIdAndStatus(UUID institutionId, ExpenseStatus status);

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.institution.id = :institutionId
              and e.status = :status
              and e.expenseDate between :fromDate and :toDate
            """)
    BigDecimal sumByStatusAndPeriod(@Param("institutionId") UUID institutionId,
            @Param("status") ExpenseStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    long countByInstitutionIdAndStatusAndExpenseDateBetween(UUID institutionId, ExpenseStatus status,
            LocalDate fromDate, LocalDate toDate);

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.institution.id = :institutionId
              and e.status = com.akira.educacion.finance.domain.ExpenseStatus.CONFIRMED
            """)
    BigDecimal sumConfirmed(@Param("institutionId") UUID institutionId);
}
