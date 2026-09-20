-- Rules, thresholds, weights and decision cut-offs are data, not code (design notes non-negotiable 3).
CREATE TABLE risk_rules (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(64)     NOT NULL,
    name                VARCHAR(128)    NOT NULL,
    description         VARCHAR(512),
    rule_type           VARCHAR(32)     NOT NULL,
    -- Which worker evaluates the rule. NULL only for decision cut-offs, which the decision engine reads.
    signal_type         VARCHAR(16),
    -- For DECISION_CUTOFF rows: the outcome reached when the risk score is >= threshold.
    decision            VARCHAR(16),
    weight              NUMERIC(6, 2)   NOT NULL DEFAULT 0,
    threshold           NUMERIC(19, 4),
    params              JSONB           NOT NULL DEFAULT '{}'::jsonb,
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_risk_rules_code UNIQUE (code),
    CONSTRAINT ck_risk_rules_rule_type CHECK (rule_type IN
        ('AMOUNT_THRESHOLD', 'VELOCITY', 'COUNTRY_RISK', 'CROSS_BORDER', 'WATCHLIST_MATCH', 'DECISION_CUTOFF')),
    CONSTRAINT ck_risk_rules_signal_type CHECK (signal_type IS NULL OR signal_type IN ('SCREENING', 'RISK')),
    CONSTRAINT ck_risk_rules_decision CHECK (decision IS NULL OR decision IN ('APPROVE', 'REVIEW', 'BLOCK')),
    CONSTRAINT ck_risk_rules_weight CHECK (weight >= 0),
    CONSTRAINT ck_risk_rules_params_object CHECK (jsonb_typeof(params) = 'object'),
    -- Cut-offs have an outcome and a threshold but no signal; every other rule has a signal and no outcome.
    CONSTRAINT ck_risk_rules_cutoff_shape CHECK (
        (rule_type = 'DECISION_CUTOFF' AND signal_type IS NULL AND decision IS NOT NULL AND threshold IS NOT NULL)
        OR
        (rule_type <> 'DECISION_CUTOFF' AND signal_type IS NOT NULL AND decision IS NULL)
    )
);

-- Workers load the enabled rules for their signal type; the engine loads enabled cut-offs.
CREATE INDEX ix_risk_rules_enabled_signal_type ON risk_rules (signal_type) WHERE enabled;
CREATE INDEX ix_risk_rules_enabled_rule_type ON risk_rules (rule_type) WHERE enabled;

CREATE TABLE watchlist_entries (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    list_source         VARCHAR(64)     NOT NULL,
    source_reference    VARCHAR(64)     NOT NULL,
    entity_type         VARCHAR(16)     NOT NULL,
    full_name           VARCHAR(200)    NOT NULL,
    -- Lower-case alphanumerics separated by single spaces; must match NameNormalizer in Java.
    normalized_name     VARCHAR(200)    NOT NULL,
    country             VARCHAR(2),
    reason              VARCHAR(256),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_watchlist_entries_source_reference UNIQUE (list_source, source_reference),
    CONSTRAINT ck_watchlist_entries_entity_type CHECK (entity_type IN ('INDIVIDUAL', 'ORGANIZATION')),
    CONSTRAINT ck_watchlist_entries_country CHECK (country IS NULL OR country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_watchlist_entries_normalized_name CHECK (normalized_name ~ '^[a-z0-9]+( [a-z0-9]+)*$')
);

-- Screening looks up active entries by normalized name.
CREATE INDEX ix_watchlist_entries_active_normalized_name ON watchlist_entries (normalized_name) WHERE active;

CREATE TABLE country_risk (
    country_code        VARCHAR(2)      PRIMARY KEY,
    country_name        VARCHAR(100)    NOT NULL,
    risk_level          VARCHAR(16)     NOT NULL,
    risk_score          INTEGER         NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_country_risk_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_country_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'PROHIBITED')),
    CONSTRAINT ck_country_risk_score CHECK (risk_score BETWEEN 0 AND 100)
);
