CREATE TABLE screening_signals (
    id                  UUID            PRIMARY KEY,
    transaction_id      UUID            NOT NULL REFERENCES transactions (id),
    event_id            UUID            NOT NULL,
    hit                 BOOLEAN         NOT NULL,
    match_score         NUMERIC(5, 4)   NOT NULL,
    matched_entry_id    BIGINT,
    matched_name        VARCHAR(200),
    details             JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- Exactly one screening signal per transaction; the unique index serves lookup by transaction.
    CONSTRAINT uq_screening_signals_transaction UNIQUE (transaction_id),
    CONSTRAINT uq_screening_signals_event UNIQUE (event_id),
    CONSTRAINT ck_screening_signals_match_score CHECK (match_score BETWEEN 0 AND 1),
    CONSTRAINT ck_screening_signals_hit_has_match
        CHECK (NOT hit OR (matched_entry_id IS NOT NULL AND matched_name IS NOT NULL))
);

CREATE TABLE risk_signals (
    id                  UUID            PRIMARY KEY,
    transaction_id      UUID            NOT NULL REFERENCES transactions (id),
    event_id            UUID            NOT NULL,
    score               NUMERIC(7, 2)   NOT NULL,
    triggered_rules     JSONB           NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_risk_signals_transaction UNIQUE (transaction_id),
    CONSTRAINT uq_risk_signals_event UNIQUE (event_id),
    CONSTRAINT ck_risk_signals_score CHECK (score >= 0),
    CONSTRAINT ck_risk_signals_triggered_rules_array CHECK (jsonb_typeof(triggered_rules) = 'array')
);

CREATE TABLE decisions (
    id                  UUID            PRIMARY KEY,
    transaction_id      UUID            NOT NULL REFERENCES transactions (id),
    decision            VARCHAR(16)     NOT NULL,
    risk_score          NUMERIC(7, 2)   NOT NULL,
    screening_hit       BOOLEAN         NOT NULL,
    reasons             JSONB           NOT NULL DEFAULT '[]'::jsonb,
    decided_at          TIMESTAMPTZ     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- A transaction is decided once.
    CONSTRAINT uq_decisions_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_decisions_decision CHECK (decision IN ('APPROVE', 'REVIEW', 'BLOCK')),
    CONSTRAINT ck_decisions_risk_score CHECK (risk_score >= 0),
    CONSTRAINT ck_decisions_reasons_array CHECK (jsonb_typeof(reasons) = 'array')
);

-- Review queue and reporting: decisions of one outcome in decision order.
CREATE INDEX ix_decisions_decision_decided_at ON decisions (decision, decided_at);
