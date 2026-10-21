package com.akira.educacion.enrollment.repository;

import com.akira.educacion.enrollment.domain.EnrollmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnrollmentHistoryRepository extends JpaRepository<EnrollmentHistory, UUID> {

    List<EnrollmentHistory> findByInstitutionIdAndEnrollmentIdOrderByOccurredAtAsc(UUID institutionId,
            UUID enrollmentId);
}
