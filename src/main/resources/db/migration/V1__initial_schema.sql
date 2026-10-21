-- Esquema inicial de la Fase 1: instituciones, usuarios, roles, permisos y auditoría.
-- Todas las tablas se crean en el esquema configurado por Flyway (DB_SCHEMA).

CREATE TABLE institution (
    id                    UUID PRIMARY KEY,
    legal_name            VARCHAR(200) NOT NULL,
    trade_name            VARCHAR(200),
    tax_id                VARCHAR(30),
    email                 VARCHAR(150),
    phone                 VARCHAR(40),
    website               VARCHAR(200),
    address               VARCHAR(250),
    city                  VARCHAR(100),
    country               VARCHAR(100) NOT NULL,
    timezone              VARCHAR(60)  NOT NULL,
    language              VARCHAR(10)  NOT NULL,
    currency              VARCHAR(3)   NOT NULL,
    logo_url              VARCHAR(500),
    primary_color         VARCHAR(20),
    secondary_color       VARCHAR(20),
    vat_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    vat_rate              NUMERIC(5, 2),
    vat_effective_from    DATE,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_institution_tax_id ON institution (tax_id) WHERE tax_id IS NOT NULL;

CREATE TABLE institution_module (
    institution_id UUID        NOT NULL REFERENCES institution (id) ON DELETE CASCADE,
    module_code    VARCHAR(40) NOT NULL,
    PRIMARY KEY (institution_id, module_code)
);

CREATE TABLE app_user (
    id             UUID PRIMARY KEY,
    institution_id UUID REFERENCES institution (id),
    email          VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(150) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE INDEX ix_app_user_institution ON app_user (institution_id);

CREATE TABLE role (
    id          UUID PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE permission (
    id          UUID PRIMARY KEY,
    code        VARCHAR(60) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE app_user_role (
    app_user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES role (id),
    PRIMARY KEY (app_user_id, role_id)
);

CREATE TABLE audit_event (
    id             UUID PRIMARY KEY,
    institution_id UUID REFERENCES institution (id),
    actor_user_id  UUID,
    actor_email    VARCHAR(150),
    entity_type    VARCHAR(60) NOT NULL,
    entity_id      VARCHAR(64),
    action         VARCHAR(30) NOT NULL,
    details        TEXT,
    occurred_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_audit_institution ON audit_event (institution_id, occurred_at DESC);
CREATE INDEX ix_audit_entity ON audit_event (entity_type, entity_id);
