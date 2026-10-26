-- =====================================================================
-- Fase 5A: comunicaciones asistidas e importación de estudiantes.
--
-- Reglas estructurales:
--  * Plantillas y comunicaciones pertenecen a una institución; las FK
--    compuestas impiden referencias cruzadas.
--  * Una comunicación conserva una instantánea del mensaje y del teléfono
--    usado. No se afirma que WhatsApp haya entregado o enviado el mensaje.
--  * La importación no persiste estudiantes durante la previsualización:
--    guarda el lote, sus filas y el resultado de la validación.
--  * El archivo Excel no se almacena: solo se registran nombre, tamaño y
--    hash para auditoría.
-- =====================================================================

CREATE TABLE message_template (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL REFERENCES institution (id),
    code           VARCHAR(40),
    name           VARCHAR(150) NOT NULL,
    body           TEXT         NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_message_template_name UNIQUE (institution_id, name),
    CONSTRAINT uk_message_template_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_message_template_code
    ON message_template (institution_id, code)
    WHERE code IS NOT NULL;

CREATE TABLE communication (
    id                    UUID PRIMARY KEY,
    institution_id        UUID         NOT NULL REFERENCES institution (id),
    student_id            UUID         NOT NULL,
    representative_id     UUID         NOT NULL,
    template_id           UUID,
    template_name         VARCHAR(150) NOT NULL,
    phone                 VARCHAR(40)  NOT NULL,
    wa_link               TEXT         NOT NULL,
    message_body          TEXT         NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    include_financial_info BOOLEAN     NOT NULL DEFAULT FALSE,
    financial_note        VARCHAR(500),
    opened_at             TIMESTAMPTZ,
    failed_reason         VARCHAR(500),
    created_by_user_id    UUID,
    created_by_email      VARCHAR(150),
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_communication_status CHECK (status IN ('DRAFT', 'READY', 'OPENED', 'FAILED')),
    CONSTRAINT ck_communication_financial CHECK (
        include_financial_info = FALSE OR financial_note IS NOT NULL
    ),
    CONSTRAINT fk_communication_student FOREIGN KEY (institution_id, student_id)
        REFERENCES student (institution_id, id),
    CONSTRAINT fk_communication_representative FOREIGN KEY (institution_id, representative_id)
        REFERENCES representative (institution_id, id),
    CONSTRAINT fk_communication_template FOREIGN KEY (institution_id, template_id)
        REFERENCES message_template (institution_id, id),
    CONSTRAINT fk_communication_created_by FOREIGN KEY (created_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uk_communication_ref UNIQUE (institution_id, id)
);

CREATE INDEX ix_communication_institution_status ON communication (institution_id, status);
CREATE INDEX ix_communication_institution_created ON communication (institution_id, created_at DESC);
CREATE INDEX ix_communication_student ON communication (institution_id, student_id);

CREATE TABLE student_import (
    id                  UUID PRIMARY KEY,
    institution_id      UUID         NOT NULL REFERENCES institution (id),
    file_name           VARCHAR(255) NOT NULL,
    file_size           BIGINT       NOT NULL,
    file_hash           VARCHAR(64)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    total_rows          INTEGER      NOT NULL DEFAULT 0,
    valid_rows          INTEGER      NOT NULL DEFAULT 0,
    error_rows          INTEGER      NOT NULL DEFAULT 0,
    duplicate_rows      INTEGER      NOT NULL DEFAULT 0,
    imported_rows       INTEGER      NOT NULL DEFAULT 0,
    updated_rows        INTEGER      NOT NULL DEFAULT 0,
    allow_update_existing BOOLEAN    NOT NULL DEFAULT FALSE,
    created_by_user_id  UUID,
    created_by_email    VARCHAR(150),
    confirmed_by_email  VARCHAR(150),
    confirmed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_student_import_status CHECK (status IN ('PREVIEWED', 'IMPORTED', 'FAILED')),
    CONSTRAINT ck_student_import_counts CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND error_rows >= 0
        AND duplicate_rows >= 0 AND imported_rows >= 0 AND updated_rows >= 0
    ),
    CONSTRAINT fk_student_import_created_by FOREIGN KEY (created_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uk_student_import_ref UNIQUE (institution_id, id)
);

CREATE INDEX ix_student_import_institution_created ON student_import (institution_id, created_at DESC);

CREATE TABLE student_import_row (
    id                  UUID PRIMARY KEY,
    institution_id      UUID         NOT NULL,
    import_id           UUID         NOT NULL,
    row_number          INTEGER      NOT NULL,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    document_type       VARCHAR(30),
    document_number     VARCHAR(40),
    birth_date          DATE,
    admission_date      DATE,
    student_status      VARCHAR(20),
    email               VARCHAR(150),
    phone               VARCHAR(40),
    address             VARCHAR(250),
    row_status          VARCHAR(30)  NOT NULL,
    errors              TEXT,
    duplicate_type      VARCHAR(30),
    existing_student_id UUID,
    created_student_id  UUID,
    updated_existing    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_student_import_row_status CHECK (
        row_status IN ('VALID', 'ERROR', 'DUPLICATE_FILE', 'DUPLICATE_EXISTING')
    ),
    CONSTRAINT ck_student_import_row_duplicate CHECK (
        duplicate_type IS NULL OR duplicate_type IN ('DOCUMENT', 'NAME_BIRTHDATE')
    ),
    CONSTRAINT ck_student_import_row_number CHECK (row_number > 1),
    CONSTRAINT fk_student_import_row_import FOREIGN KEY (institution_id, import_id)
        REFERENCES student_import (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_student_import_row_existing FOREIGN KEY (institution_id, existing_student_id)
        REFERENCES student (institution_id, id),
    CONSTRAINT fk_student_import_row_created FOREIGN KEY (institution_id, created_student_id)
        REFERENCES student (institution_id, id),
    CONSTRAINT uk_student_import_row UNIQUE (import_id, row_number)
);

CREATE INDEX ix_student_import_row_import ON student_import_row (import_id, row_number);
