-- Transactional outbox. Rows are written in the same transaction as the business change and
-- published by a relay. Relay instances claim rows instead of reading a boolean:
--
--   claim:    UPDATE ... SET status = 'CLAIMED', claimed_by = :owner, claimed_at = :now, attempts = attempts + 1
--             WHERE id IN (SELECT id ... WHERE status = 'PENDING' AND next_attempt_at <= :now
--                          OR status = 'CLAIMED' AND claimed_at < :stale_before
--                          ORDER BY created_at LIMIT :batch FOR UPDATE SKIP LOCKED)
--   publish:  UPDATE ... SET status = 'PUBLISHED' WHERE id = :id AND status = 'CLAIMED' AND claimed_by = :owner
--   retry:    back to PENDING with next_attempt_at pushed out (exponential backoff), claim cleared
--   give up:  FAILED once attempts reach the relay's configured maximum
--
-- SKIP LOCKED means concurrent relays never claim the same row. A claim that outlives the stale
-- timeout (crashed relay) is reclaimed, so delivery is at-least-once and consumers dedupe by event id.
CREATE TABLE outbox_events (
    id                  UUID            PRIMARY KEY,   -- doubles as the published event id
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        UUID            NOT NULL,      -- Kafka record key
    event_type          VARCHAR(64)     NOT NULL,
    topic               VARCHAR(249)    NOT NULL,
    payload             JSONB           NOT NULL,
    correlation_id      VARCHAR(64),
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    claimed_by          VARCHAR(128),
    claimed_at          TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    last_error          VARCHAR(2000),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_events_claimed_has_owner
        CHECK (status <> 'CLAIMED' OR (claimed_by IS NOT NULL AND claimed_at IS NOT NULL)),
    CONSTRAINT ck_outbox_events_pending_unclaimed
        CHECK (status <> 'PENDING' OR (claimed_by IS NULL AND claimed_at IS NULL)),
    CONSTRAINT ck_outbox_events_published_has_timestamp
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

-- Claim query, part 1: due pending rows in creation order. Partial, so published history doesn't bloat it.
CREATE INDEX ix_outbox_events_pending_due ON outbox_events (next_attempt_at, created_at) WHERE status = 'PENDING';
-- Claim query, part 2: stale claims left by a crashed relay.
CREATE INDEX ix_outbox_events_claimed_at ON outbox_events (claimed_at) WHERE status = 'CLAIMED';
-- Tracing an aggregate's events.
CREATE INDEX ix_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

-- Consumer idempotency: one row per (consumer group, event id), written in the consumer's transaction.
CREATE TABLE processed_events (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consumer_group      VARCHAR(128)    NOT NULL,
    event_id            UUID            NOT NULL,
    topic               VARCHAR(249)    NOT NULL,
    processed_at        TIMESTAMPTZ     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- Also serves the "already processed?" lookup.
    CONSTRAINT uq_processed_events_group_event UNIQUE (consumer_group, event_id)
);

-- Retention sweep.
CREATE INDEX ix_processed_events_processed_at ON processed_events (processed_at);
