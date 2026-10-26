package com.akira.educacion.studentimport.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Lote de importación de estudiantes. El archivo original no se almacena; solo
 * se conservan nombre, tamaño, hash y el resultado de la validación.
 */
@Entity
@Table(name = "student_import")
@Getter
@Setter
@NoArgsConstructor
public class StudentImport extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentImportStatus status = StudentImportStatus.PREVIEWED;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "updated_rows", nullable = false)
    private int updatedRows;

    @Column(name = "allow_update_existing", nullable = false)
    private boolean allowUpdateExisting;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_by_email", length = 150)
    private String createdByEmail;

    @Column(name = "confirmed_by_email", length = 150)
    private String confirmedByEmail;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
