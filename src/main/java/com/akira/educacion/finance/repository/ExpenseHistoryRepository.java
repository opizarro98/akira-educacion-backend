package com.akira.educacion.finance.repository;

import com.akira.educacion.finance.domain.ExpenseHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseHistoryRepository extends JpaRepository<ExpenseHistory, UUID> {

    List<ExpenseHistory> findByInstitutionIdAndExpenseIdOrderByOccurredAtAsc(UUID institutionId, UUID expenseId);
}
