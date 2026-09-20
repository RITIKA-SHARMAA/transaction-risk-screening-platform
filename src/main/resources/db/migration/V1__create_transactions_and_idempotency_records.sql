-- Conventions for every table in this schema:
--   * status-like columns are VARCHAR guarded by a CHECK constraint mirroring the Java enum
--     (adding an enum constant therefore needs a migration that replaces the constraint);
--   * created_at / updated_at are TIMESTAMPTZ, defaulted by the database and maintained by the
--     application (Hibernate timestamps, or explicitly in native UPDATE statements);
--   * other rows are referenced by id (FK in the database, no JPA association).

CREATE TABLE transactions (
    id                  UUID            PRIMARY KEY,
    merchant_id         VARCHAR(64)     NOT NULL,
    amount              NUMERIC(19, 4)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    payer_account_id    VARCHAR(64)     NOT NULL,
    payer_name          VARCHAR(200)    NOT NULL,
    payer_country       VARCHAR(2)      NOT NULL,
    payee_name          VARCHAR(200)    NOT NULL,
    payee_country       VARCHAR(2)      NOT NULL,
    status              VARCHAR(16)     NOT NULL,
    correlation_id      VARCHAR(64),
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_currency_iso    CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_transactions_payer_country   CHECK (payer_country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_transactions_payee_country   CHECK (payee_country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_transactions_status
        CHECK (status IN ('PENDING', 'APPROVED', 'IN_REVIEW', 'BLOCKED', 'FAILED'))
);

-- Merchant-scoped lookups (GET by id + merchant, merchant listings newest first).
CREATE INDEX ix_transactions_merchant_created ON transactions (merchant_id, created_at DESC);
-- Velocity rules: count a payer's transactions inside a time window.
CREATE INDEX ix_transactions_payer_created ON transactions (payer_account_id, created_at);

CREATE TABLE idempotency_records (
    id                  UUID            PRIMARY KEY,
    idempotency_key     VARCHAR(255)    NOT NULL,
    merchant_id         VARCHAR(64)     NOT NULL,
    request_hash        VARCHAR(64)     NOT NULL,
    status              VARCHAR(16)     NOT NULL,
    response_status     INTEGER,
    response_body       JSONB,
    transaction_id      UUID            REFERENCES transactions (id),
    expires_at          TIMESTAMPTZ     NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- The same key may be reused by different merchants, never twice by the same merchant.
    -- The unique index also serves the (key, merchant) lookup on every mutating request.
    CONSTRAINT uq_idempotency_records_key_merchant UNIQUE (idempotency_key, merchant_id),
    CONSTRAINT ck_idempotency_records_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotency_records_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_records_completed_has_response
        CHECK (status <> 'COMPLETED' OR response_status IS NOT NULL),
    CONSTRAINT ck_idempotency_records_response_status
        CHECK (response_status IS NULL OR response_status BETWEEN 100 AND 599)
);

-- Expiry sweep deletes records past their retention.
CREATE INDEX ix_idempotency_records_expires_at ON idempotency_records (expires_at);
