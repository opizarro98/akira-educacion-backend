package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {

    List<PaymentHistory> findByInstitutionIdAndPaymentIdOrderByOccurredAtAsc(UUID institutionId, UUID paymentId);
}
