-- =====================================================================
-- Fase 3A: obligaciones de cobro y conceptos financieros.
--
-- Reglas estructurales:
--  * Toda obligación pertenece a una institución y conserva las referencias
--    compuestas que impiden cruces entre instituciones.
--  * Los importes usan NUMERIC(12,2) y nunca pueden ser negativos.
--  * El IVA y los precios se copian en el detalle para que los cambios
--    posteriores del catálogo o de la configuración no alteren el historial.
--  * La idempotencia se protege con índices únicos, no solo en la capa de
--    servicio.
-- =====================================================================

CREATE TABLE billing_concept (
    id             UUID PRIMARY KEY,
    institution_id UUID           NOT NULL REFERENCES institution (id),
    code           VARCHAR(40),
    name           VARCHAR(150)   NOT NULL,
    description    VARCHAR(255),
    default_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_billing_concept_amount CHECK (default_amount >= 0),
    CONSTRAINT uk_billing_concept_name UNIQUE (institution_id, name),
    CONSTRAINT uk_billing_concept_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_billing_concept_code
    ON billing_concept (institution_id, code)
    WHERE code IS NOT NULL;

CREATE TABLE billing_settings (
    id             UUID PRIMARY KEY,
    institution_id UUID        NOT NULL REFERENCES institution (id),
    frequency      VARCHAR(20) NOT NULL,
    issue_day      INTEGER     NOT NULL,
    due_days       INTEGER     NOT NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_billing_settings_institution UNIQUE (institution_id),
    CONSTRAINT ck_billing_settings_frequency
        CHECK (frequency IN ('MONTHLY', 'BIMONTHLY', 'QUARTERLY', 'ANNUAL', 'ONE_TIME')),
    CONSTRAINT ck_billing_settings_issue_day CHECK (issue_day BETWEEN 1 AND 28),
    CONSTRAINT ck_billing_settings_due_days CHECK (due_days BETWEEN 0 AND 120)
);

CREATE TABLE charge (
    id                   UUID PRIMARY KEY,
    institution_id       UUID           NOT NULL REFERENCES institution (id),
    student_id           UUID           NOT NULL,
    enrollment_id        UUID,
    origin               VARCHAR(20)    NOT NULL,
    idempotency_key      VARCHAR(160),
    issue_date           DATE           NOT NULL,
    due_date             DATE           NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    description          VARCHAR(500),
    subtotal             NUMERIC(12, 2) NOT NULL,
    tax_amount           NUMERIC(12, 2) NOT NULL,
    total                NUMERIC(12, 2) NOT NULL,
    paid_amount          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    balance              NUMERIC(12, 2) NOT NULL,
    responsible_user_id  UUID,
    responsible_email    VARCHAR(150),
    void_reason          VARCHAR(500),
    voided_at            TIMESTAMPTZ,
    voided_by            VARCHAR(150),
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_charge_origin CHECK (origin IN ('MANUAL', 'ENROLLMENT', 'MONTHLY')),
    CONSTRAINT ck_charge_status CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'VOID')),
    CONSTRAINT ck_charge_dates CHECK (due_date >= issue_date),
    CONSTRAINT ck_charge_amounts CHECK (subtotal >= 0 AND tax_amount >= 0 AND total >= 0
        AND paid_amount >= 0 AND balance >= 0),
    CONSTRAINT ck_charge_total CHECK (total = subtotal + tax_amount),
    CONSTRAINT ck_charge_balance CHECK (
        (status = 'VOID' AND balance = 0 AND paid_amount = 0)
        OR (status <> 'VOID' AND balance = total - paid_amount)
    ),
    CONSTRAINT fk_charge_student FOREIGN KEY (institution_id, student_id)
        REFERENCES student (institution_id, id),
    CONSTRAINT fk_charge_enrollment FOREIGN KEY (institution_id, enrollment_id)
        REFERENCES enrollment (institution_id, id),
    CONSTRAINT fk_charge_responsible FOREIGN KEY (responsible_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uk_charge_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_charge_idempotency
    ON charge (institution_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Solo puede existir un cobro inicial por matrícula.
CREATE UNIQUE INDEX uk_charge_enrollment_initial
    ON charge (institution_id, enrollment_id)
    WHERE origin = 'ENROLLMENT';

CREATE INDEX ix_charge_institution_status ON charge (institution_id, status);
CREATE INDEX ix_charge_institution_issue ON charge (institution_id, issue_date DESC);
CREATE INDEX ix_charge_institution_due ON charge (institution_id, due_date);
CREATE INDEX ix_charge_student ON charge (institution_id, student_id);

CREATE TABLE charge_item (
    id             UUID PRIMARY KEY,
    institution_id UUID           NOT NULL,
    charge_id      UUID           NOT NULL,
    concept_id     UUID,
    concept_code   VARCHAR(40),
    concept_name   VARCHAR(150)   NOT NULL,
    description    VARCHAR(255),
    quantity       INTEGER        NOT NULL,
    unit_price     NUMERIC(12, 2) NOT NULL,
    discount       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    taxable_base   NUMERIC(12, 2) NOT NULL,
    vat_rate       NUMERIC(5, 2)  NOT NULL,
    vat_amount     NUMERIC(12, 2) NOT NULL,
    total          NUMERIC(12, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_charge_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_charge_item_amounts CHECK (unit_price >= 0 AND discount >= 0
        AND taxable_base >= 0 AND vat_rate >= 0 AND vat_amount >= 0 AND total >= 0),
    CONSTRAINT ck_charge_item_base CHECK (taxable_base = quantity * unit_price - discount),
    CONSTRAINT ck_charge_item_total CHECK (total = taxable_base + vat_amount),
    CONSTRAINT ck_charge_item_discount CHECK (discount <= quantity * unit_price),
    CONSTRAINT fk_charge_item_charge FOREIGN KEY (institution_id, charge_id)
        REFERENCES charge (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_charge_item_concept FOREIGN KEY (institution_id, concept_id)
        REFERENCES billing_concept (institution_id, id)
);

CREATE INDEX ix_charge_item_charge ON charge_item (institution_id, charge_id);

CREATE TABLE charge_history (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL,
    charge_id      UUID         NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20),
    reason         VARCHAR(500),
    details        VARCHAR(1000),
    actor_email    VARCHAR(150),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_charge_history_charge FOREIGN KEY (institution_id, charge_id)
        REFERENCES charge (institution_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_charge_history_charge
    ON charge_history (institution_id, charge_id, occurred_at);
