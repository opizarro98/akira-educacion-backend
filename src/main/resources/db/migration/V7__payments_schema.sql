-- =====================================================================
-- Fase 3B: pagos, aplicaciones y recibos internos.
--
-- Reglas estructurales:
--  * Todo documento financiero pertenece a una institución y usa claves
--    foráneas compuestas para impedir referencias cruzadas.
--  * Los importes usan NUMERIC(12,2) y son mayores que cero.
--  * No se elimina información financiera: los pagos se anulan y las
--    aplicaciones se revierten dejando rastro.
--  * La idempotencia se protege con índices únicos además de la capa de
--    servicio.
-- =====================================================================

CREATE TABLE payment (
    id                      UUID PRIMARY KEY,
    institution_id          UUID           NOT NULL REFERENCES institution (id),
    student_id              UUID           NOT NULL,
    amount                  NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    method                  VARCHAR(20)    NOT NULL,
    reference               VARCHAR(100),
    received_date           DATE           NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    applied_amount          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    registered_by_user_id   UUID,
    registered_by_email     VARCHAR(150),
    confirmed_by_user_id    UUID,
    confirmed_by_email      VARCHAR(150),
    confirmed_at            TIMESTAMPTZ,
    notes                   VARCHAR(500),
    idempotency_key         VARCHAR(160),
    void_reason             VARCHAR(500),
    voided_at               TIMESTAMPTZ,
    voided_by               VARCHAR(150),
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_applied CHECK (applied_amount >= 0 AND applied_amount <= amount),
    CONSTRAINT ck_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING_VERIFICATION', 'CONFIRMED', 'VOID')),
    CONSTRAINT ck_payment_void CHECK (status <> 'VOID' OR applied_amount = 0),
    CONSTRAINT fk_payment_student FOREIGN KEY (institution_id, student_id)
        REFERENCES student (institution_id, id),
    CONSTRAINT fk_payment_registered_by FOREIGN KEY (registered_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_payment_confirmed_by FOREIGN KEY (confirmed_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uk_payment_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_payment_idempotency
    ON payment (institution_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_payment_institution_status ON payment (institution_id, status);
CREATE INDEX ix_payment_institution_received ON payment (institution_id, received_date DESC);
CREATE INDEX ix_payment_student ON payment (institution_id, student_id);

CREATE TABLE payment_allocation (
    id              UUID PRIMARY KEY,
    institution_id  UUID           NOT NULL,
    payment_id      UUID           NOT NULL,
    charge_id       UUID           NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    operation_key   VARCHAR(160),
    reversed        BOOLEAN        NOT NULL DEFAULT FALSE,
    reversed_at     TIMESTAMPTZ,
    reversed_by     VARCHAR(150),
    reversal_reason VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_allocation_amount CHECK (amount > 0),
    CONSTRAINT fk_payment_allocation_payment FOREIGN KEY (institution_id, payment_id)
        REFERENCES payment (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_allocation_charge FOREIGN KEY (institution_id, charge_id)
        REFERENCES charge (institution_id, id),
    CONSTRAINT uk_payment_allocation_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_payment_allocation_active
    ON payment_allocation (institution_id, payment_id, charge_id)
    WHERE reversed = FALSE;

-- La clave de operación se comparte entre todas las aplicaciones de un mismo
-- lote; la idempotencia se valida en el servicio con el pago bloqueado.
CREATE INDEX ix_payment_allocation_operation
    ON payment_allocation (institution_id, payment_id, operation_key)
    WHERE operation_key IS NOT NULL;

CREATE INDEX ix_payment_allocation_charge ON payment_allocation (institution_id, charge_id);

CREATE TABLE receipt (
    id              UUID PRIMARY KEY,
    institution_id  UUID           NOT NULL,
    payment_id      UUID           NOT NULL,
    receipt_number  VARCHAR(30)    NOT NULL,
    sequence_number BIGINT         NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    issued_at       TIMESTAMPTZ    NOT NULL,
    issued_by_email VARCHAR(150),
    voided          BOOLEAN        NOT NULL DEFAULT FALSE,
    void_reason     VARCHAR(500),
    voided_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_receipt_amount CHECK (amount > 0),
    CONSTRAINT ck_receipt_sequence CHECK (sequence_number > 0),
    CONSTRAINT fk_receipt_payment FOREIGN KEY (institution_id, payment_id)
        REFERENCES payment (institution_id, id) ON DELETE CASCADE,
    CONSTRAINT uk_receipt_number UNIQUE (institution_id, receipt_number),
    CONSTRAINT uk_receipt_payment UNIQUE (institution_id, payment_id)
);

CREATE TABLE receipt_counter (
    institution_id UUID PRIMARY KEY REFERENCES institution (id),
    last_number    BIGINT NOT NULL,
    CONSTRAINT ck_receipt_counter CHECK (last_number >= 0)
);

CREATE TABLE payment_history (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL,
    payment_id     UUID         NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    details        VARCHAR(1000),
    reason         VARCHAR(500),
    actor_email    VARCHAR(150),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_payment_history_payment FOREIGN KEY (institution_id, payment_id)
        REFERENCES payment (institution_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_payment_history_payment
    ON payment_history (institution_id, payment_id, occurred_at);
