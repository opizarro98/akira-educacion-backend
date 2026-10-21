package com.akira.educacion.students.repository;

import com.akira.educacion.students.domain.StudentRepresentative;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepresentativeRepository extends JpaRepository<StudentRepresentative, UUID> {

    @EntityGraph(attributePaths = "representative")
    List<StudentRepresentative> findByStudentIdOrderByPrimaryContactDesc(UUID studentId);

    @EntityGraph(attributePaths = "student")
    List<StudentRepresentative> findByRepresentativeIdOrderByStudentLastNameAsc(UUID representativeId);

    Optional<StudentRepresentative> findByStudentIdAndRepresentativeId(UUID studentId, UUID representativeId);

    Optional<StudentRepresentative> findFirstByStudentIdAndPrimaryContactTrue(UUID studentId);

    long countByStudentId(UUID studentId);

    long countByRepresentativeId(UUID representativeId);
}
