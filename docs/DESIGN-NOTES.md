# Design notes

Engineering notes for **transaction-risk-screening-platform**. Read this before changing code.
The diagram version of the flow lives in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Status

Phase 1 (skeleton) is done: build, packages, configuration, Docker, actuator. **No business logic,
entities, migrations, listeners or endpoints exist yet.** Everything below the "Architecture" heading
describes the target design that later phases must implement, not code that is already there.

## Stack

- Java 21, Spring Boot 3.3, Maven (use `./mvnw`)
- PostgreSQL 16 + Flyway, Spring Data JPA (Hibernate, `ddl-auto: validate`)
- Kafka (KRaft, no ZooKeeper) via spring-kafka
- Redis via spring-data-redis
- Spring Security, Actuator, Micrometer Prometheus registry
- Lombok (restricted, see Conventions)
- Tests: JUnit 5, Spring Boot Test, Testcontainers (Postgres, Kafka, Redis), Awaitility

## Running

```bash
docker compose up --build          # postgres, redis, kafka and the app
curl localhost:8080/actuator/health
curl localhost:8080/actuator/prometheus
```

Run the app from the IDE/host against the compose infrastructure instead:

```bash
docker compose up -d postgres redis kafka
./mvnw spring-boot:run             # profile "local" is the default
```

Host ports: app 8080, Postgres **5433** (avoids clashing with a local Postgres on 5432), Redis 6379,
Kafka 9094 (EXTERNAL listener). Inside the compose network Kafka is `kafka:9092`. All host ports are
overridable (`POSTGRES_HOST_PORT`, `REDIS_HOST_PORT`, `KAFKA_HOST_PORT`, `APP_HOST_PORT`).

Tests need a Docker daemon (Testcontainers):

```bash
./mvnw test
```

Testcontainers is pinned to 1.21.4 in `pom.xml` because the 1.19.8 version managed by Boot 3.3 speaks
Docker API 1.32, which Docker Engine 29+ rejects. With Colima, export
`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
The Kafka test container is `org.testcontainers.containers.KafkaContainer` (Confluent image, KRaft)
because Boot 3.3's `@ServiceConnection` does not recognise the `apache/kafka` container class.

**Kafka images differ by context:** tests use the Confluent image (`confluentinc/cp-kafka`, required by Boot 3.3 test autoconfiguration) while docker-compose uses `apache/kafka`; both run in KRaft mode.

## Configuration

- `src/main/resources/application.yml` holds a base section plus `local` and `test` profile documents.
- Every value is read through `${ENV_VAR:default}`. The base section has **no defaults for credentials
  or hosts** (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`); only
  the `local` profile supplies throwaway values matching docker-compose.
- The `test` profile gets connection details from Testcontainers `@ServiceConnection`.
- Never commit real secrets. `.env` files are git-ignored.
- Actuator exposes `health`, `info`, `prometheus`. Health and prometheus are unauthenticated;
  everything else requires authentication (API auth mechanism not yet chosen, unauthenticated calls get 401).

## Package layout (`com.ritikasharma.risk`)

| Package     | Responsibility |
|-------------|----------------|
| `api`       | REST controllers, request/response DTOs, Idempotency-Key handling |
| `domain`    | Entities, repositories, domain types (Transaction, Decision, ...) |
| `screening` | Screening worker: consumes `txn.submitted`, emits `txn.screening-signal` |
| `risk`      | Risk worker: consumes `txn.submitted`, evaluates DB-driven rules, emits `txn.risk-signal` |
| `decision`  | Decision engine: joins both signals, decides APPROVE / REVIEW / BLOCK, writes outbox |
| `messaging` | Topic names (`Topics`), producers, outbox relay, processed-event store, retry/DLT wiring |
| `config`    | Spring configuration (security, Kafka topics and error handlers, Redis, observability) |
| `common`    | Correlation id, RFC 7807 problem details, shared utilities |

## Architecture

```
REST API -> Transaction Service -> Kafka (txn.submitted)
                                     ├─> Screening Worker -> txn.screening-signal ─┐
                                     └─> Risk Worker      -> txn.risk-signal      ─┴─> Decision Engine
Decision Engine -> DB (decision + outbox row, one transaction) -> Outbox Relay -> txn.decided
Outcome: APPROVE | REVIEW | BLOCK
Poison events on any topic -> <topic>.DLT
```

1. **REST API** accepts a transaction submission. Requires an `Idempotency-Key` header.
2. **Transaction Service** validates, persists the transaction (status `PENDING`) and publishes
   `txn.submitted`, keyed by transaction id so every event for a transaction lands on one partition.
3. **Screening Worker** and **Risk Worker** consume `txn.submitted` independently, in parallel, each in
   its own consumer group. Each emits exactly one signal per transaction.
4. **Decision Engine** consumes `txn.screening-signal` and `txn.risk-signal`, stores each signal, and
   decides once both signals for a transaction are present. It is the only component that decides.
5. The decision row and a `txn.decided` outbox row are written **in the same database transaction**.
   An outbox relay publishes pending outbox rows to `txn.decided` and marks them sent.
6. Consumers that fail after retries send the record to the matching `.DLT` topic.

### Topics

| Topic                  | Producer            | Consumers                    | Key            |
|------------------------|---------------------|------------------------------|----------------|
| `txn.submitted`        | Transaction Service | Screening Worker, Risk Worker | transaction id |
| `txn.screening-signal` | Screening Worker    | Decision Engine              | transaction id |
| `txn.risk-signal`      | Risk Worker         | Decision Engine              | transaction id |
| `txn.decided`          | Outbox Relay        | downstream systems           | transaction id |

Each has a `.DLT` twin: `txn.submitted.DLT`, `txn.screening-signal.DLT`, `txn.risk-signal.DLT`,
`txn.decided.DLT`. All eight are declared in `config/KafkaTopicConfig` and names live in `messaging/Topics`.
Broker auto-topic-creation is disabled in compose, so a new topic must be declared there.

### Non-negotiables

These are requirements, not suggestions. Do not ship a feature that violates one.

1. **Request idempotency.** Mutating endpoints require an `Idempotency-Key` header. A repeated key with
   the same payload returns the original response; the same key with a different payload is rejected
   (422 problem detail). A missing key is a 400 problem detail. Key records are persisted, not only cached.
2. **Consumer idempotency.** Every event carries a unique event id. Consumers record processed event ids
   (per consumer) in the same database transaction as their side effects and skip ids already seen.
   Kafka delivery is at-least-once; correctness must never depend on exactly-once delivery.
3. **DB-driven risk rules.** Thresholds, weights, rule enablement and decision cut-offs live in database
   tables managed by Flyway migrations. Never hardcode them as Java constants, enums with values, or
   properties.
4. **Transactional outbox for `txn.decided`.** Never call `KafkaTemplate.send` for `txn.decided` inside
   or after the decision transaction. Write an outbox row in the same transaction; the relay publishes it.
5. **Retry then DLT.** Consumer errors use `DefaultErrorHandler` with `ExponentialBackOff` and a
   `DeadLetterPublishingRecoverer` to `<topic>.DLT`. Non-retryable exceptions (deserialization,
   validation) go straight to the DLT. No infinite retries, no silently swallowed exceptions.
6. **Correlation id propagation.** Read `X-Correlation-Id` from the HTTP request (generate one if absent),
   put it in the MDC, return it in the response header, add it as a Kafka header on every produced
   record (including outbox-relayed and DLT records), and restore it into the MDC in every consumer.
   Outbox rows store the correlation id so the relay can propagate it.

## Conventions

- **Constructor injection only.** No `@Autowired` on fields or setters. `@RequiredArgsConstructor` is
  acceptable on non-entity Spring beans.
- **DTOs are separate from entities.** Controllers and Kafka payloads never expose JPA entities. Prefer
  Java records for DTOs and event payloads.
- **RFC 7807 problem details for every error.** `spring.mvc.problemdetails.enabled` is on; a
  `@RestControllerAdvice` in `common` must map domain and validation exceptions to `ProblemDetail`.
  No ad-hoc error JSON shapes.
- **Flyway for every schema change.** Migrations go in `src/main/resources/db/migration` as
  `V<n>__<description>.sql`. Never edit an applied migration; add a new one. Hibernate only validates.
- **No Lombok on JPA entities that take part in relationships.** Generated `equals`/`hashCode`/`toString`
  on associations cause lazy-loading and recursion bugs. Write those entities by hand.
- Kafka messages are keyed by transaction id.
- Store money as `BigDecimal` / `NUMERIC` with an ISO 4217 currency code, and timestamps as `Instant` / `TIMESTAMPTZ` in UTC.
- Integration tests use Testcontainers via `TestcontainersConfiguration`, not mocks of Kafka or Postgres.
  Use Awaitility for async assertions and never `Thread.sleep`.
