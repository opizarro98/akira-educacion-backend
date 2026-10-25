package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.ChargeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChargeHistoryRepository extends JpaRepository<ChargeHistory, UUID> {

    List<ChargeHistory> findByInstitutionIdAndChargeIdOrderByOccurredAtAsc(UUID institutionId, UUID chargeId);
}
