# Architecture

Event-driven payment risk screening. A submitted transaction is screened and risk-scored in parallel;
a decision engine combines both signals into **APPROVE**, **REVIEW** or **BLOCK**.

## Flow

```mermaid
flowchart LR
    client([Client])

    subgraph app[payment-risk-screening-platform]
        api[REST API<br/>Idempotency-Key<br/>X-Correlation-Id]
        txn[Transaction Service]
        screening[Screening Worker]
        risk[Risk Worker]
        decision[Decision Engine]
        relay[Outbox Relay]
    end

    subgraph kafka[Kafka]
        submitted[[txn.submitted]]
        ssignal[[txn.screening-signal]]
        rsignal[[txn.risk-signal]]
        decided[[txn.decided]]
        dlt[[*.DLT]]
    end

    db[(PostgreSQL<br/>transactions · risk rules<br/>decisions · outbox<br/>processed events)]

    client -->|POST transaction| api --> txn
    txn -->|persist PENDING| db
    txn --> submitted

    submitted --> screening
    submitted --> risk
    risk -.->|load rules| db
    screening --> ssignal
    risk --> rsignal

    ssignal --> decision
    rsignal --> decision
    decision -->|decision + outbox row<br/>one DB transaction| db

    db -.->|poll pending outbox| relay
    relay --> decided
    decided --> outcome{APPROVE<br/>REVIEW<br/>BLOCK}

    screening -.->|retries exhausted| dlt
    risk -.->|retries exhausted| dlt
    decision -.->|retries exhausted| dlt
```

## Components

| Component | Consumes | Produces | Notes |
|-----------|----------|----------|-------|
| REST API | HTTP | — | Requires `Idempotency-Key`; propagates `X-Correlation-Id`; RFC 7807 errors |
| Transaction Service | — | `txn.submitted` | Persists the transaction before publishing |
| Screening Worker | `txn.submitted` | `txn.screening-signal` | Own consumer group |
| Risk Worker | `txn.submitted` | `txn.risk-signal` | Rules, thresholds and weights come from the DB |
| Decision Engine | both signal topics | outbox row | Decides only when both signals are present |
| Outbox Relay | outbox table | `txn.decided` | At-least-once publish; consumers dedupe by event id |

## Reliability guarantees

- **Request idempotency.** `Idempotency-Key` replay returns the original response.
- **Consumer idempotency.** Processed event ids are recorded in the same transaction as side effects.
- **Transactional outbox.** `txn.decided` is never lost or published for a rolled-back decision.
- **Retry → DLT.** Exponential backoff, then `<topic>.DLT`. Non-retryable errors skip retries.
- **Traceability.** The correlation id flows HTTP → MDC → Kafka headers → consumer MDC → outbox → `txn.decided`.

## Decision sequence

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant A as REST API
    participant K as Kafka
    participant S as Screening Worker
    participant R as Risk Worker
    participant D as Decision Engine
    participant DB as PostgreSQL
    participant O as Outbox Relay

    C->>A: POST /transactions (Idempotency-Key, X-Correlation-Id)
    A->>DB: store transaction PENDING + idempotency record
    A->>K: txn.submitted
    A-->>C: 202 Accepted
    par screening
        K->>S: txn.submitted
        S->>K: txn.screening-signal
    and risk scoring
        K->>R: txn.submitted
        R->>DB: load active risk rules
        R->>K: txn.risk-signal
    end
    K->>D: screening signal
    K->>D: risk signal
    D->>DB: BEGIN decision + outbox row + processed event id COMMIT
    O->>DB: poll unsent outbox rows
    O->>K: txn.decided (APPROVE / REVIEW / BLOCK)
    O->>DB: mark outbox row sent
```
