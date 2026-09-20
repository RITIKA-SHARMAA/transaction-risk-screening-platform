CREATE TABLE review_actions (
    id                  UUID PRIMARY KEY,
    transaction_id      UUID NOT NULL REFERENCES transactions (id),
    reviewer_username   VARCHAR(64) NOT NULL,
    decision            VARCHAR(16) NOT NULL,
    comment             VARCHAR(1000),
    reviewed_at         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_review_actions_decision CHECK (decision IN ('APPROVE', 'BLOCK')),
    CONSTRAINT uq_review_actions_transaction UNIQUE (transaction_id)
);
CREATE INDEX ix_review_actions_reviewed_at ON review_actions (reviewed_at);
