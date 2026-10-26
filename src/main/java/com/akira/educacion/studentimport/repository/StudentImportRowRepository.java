package com.akira.educacion.studentimport.repository;

import com.akira.educacion.studentimport.domain.StudentImportRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentImportRowRepository extends JpaRepository<StudentImportRow, UUID> {

    List<StudentImportRow> findByImportIdOrderByRowNumberAsc(UUID importId);
}
