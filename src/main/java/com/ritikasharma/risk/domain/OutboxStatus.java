package com.ritikasharma.risk.domain;

/** Mirrors ck_outbox_events_status. See V4 migration for the claim protocol. */
public enum OutboxStatus {
    PENDING,
    CLAIMED,
    PUBLISHED,
    FAILED
}
