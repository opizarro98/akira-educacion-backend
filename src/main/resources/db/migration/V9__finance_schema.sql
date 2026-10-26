-- =====================================================================
-- Fase 4: categorías de egreso, egresos y movimientos de caja.
--
-- Reglas estructurales:
--  * Todo documento financiero pertenece a una institución y usa claves
--    foráneas compuestas para impedir referencias cruzadas.
--  * Los importes usan NUMERIC(12,2) y son mayores que cero.
--  * No se elimina información financiera: los egresos se anulan y su
--    historial es append-only.
--  * Los movimientos de caja son derivados: cada operación confirmada
--    genera como máximo un movimiento por tipo y origen (idempotencia en
--    base de datos, no solo en el servicio).
--  * Las obligaciones NO son dinero recibido: los saldos se consultan en
--    charge y el efectivo únicamente en cash_movement / payment.
-- =====================================================================

CREATE TABLE expense_category (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL REFERENCES institution (id),
    code           VARCHAR(40),
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(255),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_category_name UNIQUE (institution_id, name),
    CONSTRAINT uk_expense_category_ref UNIQUE (institution_id, id)
);

CREATE UNIQUE INDEX uk_expense_category_code
    ON expense_category (institution_id, code)
    WHERE code IS NOT NULL;

CREATE TABLE expense (
    id                   UUID PRIMARY KEY,
    institution_id       UUID           NOT NULL REFERENCES institution (id),
    category_id          UUID           NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    expense_date         DATE           NOT NULL,
    description          VARCHAR(500)   NOT NULL,
    method               VARCHAR(20)    NOT NULL,
    beneficiary          VARCHAR(200),
    reference            VARCHAR(100),
    status               VARCHAR(20)    NOT NULL,
    responsible_user_id  UUID,
    responsible_email    VARCHAR(150),
    confirmed_at         TIMESTAMPTZ,
    confirmed_by_email   VARCHAR(150),
    void_reason          VARCHAR(500),
    voided_at            TIMESTAMPTZ,
    voided_by            VARCHAR(150),
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_expense_method CHECK (method IN ('CASH', 'BANK_TRANSFER')),
    CONSTRAINT ck_expense_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOID')),
    CONSTRAINT ck_expense_void CHECK (
        (status <> 'VOID') OR (void_reason IS NOT NULL AND voided_at IS NOT NULL)
    ),
    CONSTRAINT fk_expense_category FOREIGN KEY (institution_id, category_id)
        REFERENCES expense_category (institution_id, id),
    CONSTRAINT fk_expense_responsible FOREIGN KEY (responsible_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uk_expense_ref UNIQUE (institution_id, id)
);

CREATE INDEX ix_expense_institution_status ON expense (institution_id, status);
CREATE INDEX ix_expense_institution_date ON expense (institution_id, expense_date DESC);
CREATE INDEX ix_expense_category ON expense (institution_id, category_id);

CREATE TABLE expense_history (
    id             UUID PRIMARY KEY,
    institution_id UUID         NOT NULL,
    expense_id     UUID         NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    details        VARCHAR(1000),
    reason         VARCHAR(500),
    actor_email    VARCHAR(150),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_expense_history_expense FOREIGN KEY (institution_id, expense_id)
        REFERENCES expense (institution_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_expense_history_expense
    ON expense_history (institution_id, expense_id, occurred_at);

CREATE TABLE cash_movement (
    id             UUID PRIMARY KEY,
    institution_id UUID           NOT NULL REFERENCES institution (id),
    movement_type  VARCHAR(30)    NOT NULL,
    source_type    VARCHAR(20)    NOT NULL,
    payment_id     UUID,
    expense_id     UUID,
    amount         NUMERIC(12, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    occurred_at    TIMESTAMPTZ    NOT NULL,
    description    VARCHAR(500),
    actor_email    VARCHAR(150),
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_cash_movement_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_movement_type CHECK (
        movement_type IN ('PAYMENT_IN', 'EXPENSE_OUT', 'PAYMENT_VOID_REVERSAL', 'EXPENSE_VOID_REVERSAL')
    ),
    CONSTRAINT ck_cash_movement_source CHECK (source_type IN ('PAYMENT', 'EXPENSE')),
    CONSTRAINT ck_cash_movement_reference CHECK (
        (source_type = 'PAYMENT' AND movement_type IN ('PAYMENT_IN', 'PAYMENT_VOID_REVERSAL')
            AND payment_id IS NOT NULL AND expense_id IS NULL)
        OR (source_type = 'EXPENSE' AND movement_type IN ('EXPENSE_OUT', 'EXPENSE_VOID_REVERSAL')
            AND expense_id IS NOT NULL AND payment_id IS NULL)
    ),
    -- Las FK compuestas impiden que un movimiento apunte a un documento de
    -- otra institución, incluso si se intentara insertar directamente en SQL.
    CONSTRAINT fk_cash_movement_payment FOREIGN KEY (institution_id, payment_id)
        REFERENCES payment (institution_id, id),
    CONSTRAINT fk_cash_movement_expense FOREIGN KEY (institution_id, expense_id)
        REFERENCES expense (institution_id, id),
    CONSTRAINT uk_cash_movement_ref UNIQUE (institution_id, id)
);

CREATE INDEX ix_cash_movement_occurred ON cash_movement (institution_id, occurred_at DESC);

-- Como máximo un movimiento por operación y tipo: idempotencia en la base.
CREATE UNIQUE INDEX uk_cash_movement_operation
    ON cash_movement (institution_id, movement_type, coalesce(payment_id, expense_id));

CREATE INDEX ix_cash_movement_payment ON cash_movement (institution_id, payment_id)
    WHERE payment_id IS NOT NULL;
CREATE INDEX ix_cash_movement_expense ON cash_movement (institution_id, expense_id)
    WHERE expense_id IS NOT NULL;
