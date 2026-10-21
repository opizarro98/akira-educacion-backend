package com.akira.educacion.enrollment.repository;

import com.akira.educacion.enrollment.domain.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    @EntityGraph(attributePaths = { "student", "educationalLevel", "plan", "academicPeriod", "services" })
    Optional<Enrollment> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByStudentId(UUID studentId);

    @EntityGraph(attributePaths = { "student", "educationalLevel", "plan", "academicPeriod", "services" })
    List<Enrollment> findByInstitutionIdAndStudentIdOrderByCreatedAtDesc(UUID institutionId, UUID studentId);

    @Query("""
            select e from Enrollment e
            where e.institution.id = :institutionId
              and (:hasStatus = false or cast(e.status as string) = :status)
              and (:hasStudent = false or e.student.id = :studentId)
              and (:hasPeriod = false or e.academicPeriod.id = :academicPeriodId)
            """)
    Page<Enrollment> search(@Param("institutionId") UUID institutionId,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") String status,
            @Param("hasStudent") boolean hasStudent,
            @Param("studentId") UUID studentId,
            @Param("hasPeriod") boolean hasPeriod,
            @Param("academicPeriodId") UUID academicPeriodId,
            Pageable pageable);
}
