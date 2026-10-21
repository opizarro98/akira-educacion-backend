-- =====================================================================
-- Fase 2: gestión escolar (estudiantes, representantes),
-- catálogo (niveles, períodos, servicios, planes, tarifas) y matrículas.
--
-- Reglas de integridad institucional:
--  * Toda entidad de negocio lleva institution_id.
--  * Las referencias entre entidades se validan con claves foráneas
--    compuestas (institution_id, id) para impedir referencias cruzadas
--    entre instituciones incluso ante errores de la capa de servicio.
-- =====================================================================

CREATE TABLE educational_level (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL REFERENCES institution (id),
    name           VARCHAR(120) NOT NULL,
    code           VARCHAR(40),
    description    VARCHAR(255),
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_educational_level_name UNIQUE (institution_id, name),
    CONSTRAINT uk_educational_level_ref UNIQUE (institution_id, id)
);

CREATE TABLE academic_period (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL REFERENCES institution (id),
    name           VARCHAR(120) NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_academic_period_name UNIQUE (institution_id, name),
    CONSTRAINT uk_academic_period_ref UNIQUE (institution_id, id),
    CONSTRAINT ck_academic_period_dates CHECK (end_date >= start_date)
);

CREATE TABLE catalog_service (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL REFERENCES institution (id),
    name           VARCHAR(150) NOT NULL,
    code           VARCHAR(40),
    description    VARCHAR(255),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_catalog_service_name UNIQUE (institution_id, name),
    CONSTRAINT uk_catalog_service_ref UNIQUE (institution_id, id)
);

CREATE TABLE plan (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL REFERENCES institution (id),
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(255),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_name UNIQUE (institution_id, name),
    CONSTRAINT uk_plan_ref UNIQUE (institution_id, id)
);

CREATE TABLE plan_service (
    id             UUID PRIMARY KEY,
    institution_id UUID        NOT NULL,
    plan_id        UUID        NOT NULL,
    service_id     UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_plan_service_plan FOREIGN KEY (institution_id, plan_id)
        REFERENCES plan (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_service_service FOREIGN KEY (institution_id, service_id)
        REFERENCES catalog_service (institution_id, id),
    CONSTRAINT uk_plan_service UNIQUE (plan_id, service_id)
);

CREATE TABLE service_rate (
    id             UUID PRIMARY KEY,
    institution_id UUID           NOT NULL,
    service_id     UUID           NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    valid_from     DATE           NOT NULL,
    valid_to       DATE,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_service_rate_service FOREIGN KEY (institution_id, service_id)
        REFERENCES catalog_service (institution_id, id),
    CONSTRAINT uk_service_rate_ref UNIQUE (institution_id, id),
    CONSTRAINT ck_service_rate_amount CHECK (amount >= 0),
    CONSTRAINT ck_service_rate_range CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_service_rate_service ON service_rate (institution_id, service_id, valid_from DESC);

CREATE TABLE student (
    id              UUID PRIMARY KEY,
    institution_id  UUID         NOT NULL REFERENCES institution (id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    document_type   VARCHAR(30),
    document_number VARCHAR(40),
    birth_date      DATE,
    admission_date  DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    email           VARCHAR(150),
    phone           VARCHAR(40),
    address         VARCHAR(250),
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_student_status CHECK (status IN ('PRE_REGISTERED', 'ACTIVE', 'WITHDRAWN', 'GRADUATED')),
    CONSTRAINT uk_student_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_student_document
    ON student (institution_id, document_number)
    WHERE document_number IS NOT NULL;

CREATE INDEX ix_student_institution_status ON student (institution_id, status);
CREATE INDEX ix_student_name_search ON student (institution_id, lower(last_name), lower(first_name));

CREATE TABLE representative (
    id              UUID PRIMARY KEY,
    institution_id  UUID         NOT NULL REFERENCES institution (id),
    full_name       VARCHAR(200) NOT NULL,
    document_number VARCHAR(40),
    phone           VARCHAR(40),
    email           VARCHAR(150),
    relationship    VARCHAR(60),
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_representative_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_representative_document
    ON representative (institution_id, document_number)
    WHERE document_number IS NOT NULL;

CREATE INDEX ix_representative_name ON representative (institution_id, lower(full_name));

CREATE TABLE student_representative (
    id                             UUID PRIMARY KEY,
    institution_id                 UUID        NOT NULL,
    student_id                     UUID        NOT NULL,
    representative_id              UUID        NOT NULL,
    primary_contact                BOOLEAN     NOT NULL DEFAULT FALSE,
    authorized_for_communications  BOOLEAN     NOT NULL DEFAULT TRUE,
    authorized_for_billing         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                     TIMESTAMPTZ NOT NULL,
    updated_at                     TIMESTAMPTZ NOT NULL,
    version                        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_student_representative_student FOREIGN KEY (institution_id, student_id)
        REFERENCES student (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_student_representative_representative FOREIGN KEY (institution_id, representative_id)
        REFERENCES representative (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT uk_student_representative UNIQUE (student_id, representative_id)
);

CREATE INDEX ix_student_representative_representative
    ON student_representative (institution_id, representative_id);

CREATE TABLE enrollment (
    id                   UUID PRIMARY KEY,
    institution_id       UUID           NOT NULL REFERENCES institution (id),
    student_id           UUID           NOT NULL,
    educational_level_id UUID           NOT NULL,
    plan_id              UUID,
    academic_period_id   UUID,
    start_date           DATE           NOT NULL,
    end_date             DATE,
    agreed_price         NUMERIC(12, 2) NOT NULL,
    vat_rate             NUMERIC(5, 2),
    vat_amount           NUMERIC(12, 2),
    total_amount         NUMERIC(12, 2),
    status               VARCHAR(20)    NOT NULL,
    cancellation_reason  VARCHAR(500),
    notes                VARCHAR(500),
    confirmed_at         TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_enrollment_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_enrollment_price CHECK (agreed_price >= 0),
    CONSTRAINT ck_enrollment_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (institution_id, student_id)
        REFERENCES student (institution_id, id),
    CONSTRAINT fk_enrollment_level FOREIGN KEY (institution_id, educational_level_id)
        REFERENCES educational_level (institution_id, id),
    CONSTRAINT fk_enrollment_plan FOREIGN KEY (institution_id, plan_id)
        REFERENCES plan (institution_id, id),
    CONSTRAINT fk_enrollment_period FOREIGN KEY (institution_id, academic_period_id)
        REFERENCES academic_period (institution_id, id),
    CONSTRAINT uk_enrollment_ref UNIQUE (institution_id, id)
);

CREATE INDEX ix_enrollment_institution_status ON enrollment (institution_id, status);
CREATE INDEX ix_enrollment_student ON enrollment (institution_id, student_id);

CREATE TABLE enrollment_service (
    id             UUID PRIMARY KEY,
    institution_id UUID           NOT NULL,
    enrollment_id  UUID           NOT NULL,
    service_id     UUID           NOT NULL,
    quantity       INTEGER        NOT NULL DEFAULT 1,
    agreed_price   NUMERIC(12, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_enrollment_service_quantity CHECK (quantity > 0),
    CONSTRAINT ck_enrollment_service_price CHECK (agreed_price >= 0),
    CONSTRAINT fk_enrollment_service_enrollment FOREIGN KEY (institution_id, enrollment_id)
        REFERENCES enrollment (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_service_service FOREIGN KEY (institution_id, service_id)
        REFERENCES catalog_service (institution_id, id),
    CONSTRAINT uk_enrollment_service UNIQUE (enrollment_id, service_id)
);

CREATE TABLE enrollment_history (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL,
    enrollment_id  UUID         NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20),
    reason         VARCHAR(500),
    actor_email    VARCHAR(150),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_enrollment_history_enrollment FOREIGN KEY (institution_id, enrollment_id)
        REFERENCES enrollment (institution_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_enrollment_history_enrollment
    ON enrollment_history (institution_id, enrollment_id, occurred_at);
