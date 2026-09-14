# Design notes

Engineering notes for **transaction-risk-screening-platform**. Read this before changing code.
The diagram version of the flow lives in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Status

Nine build phases, numbered 0-8. Keep this list, the README's "Implementation status" table and the code in agreement.

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Skeleton: build, packages, configuration, Docker, actuator | Done |
| 1 | Persistence: Flyway schema and seed data, entities, repositories, repository tests | Done |
| 2 | Authentication: JWT login, MERCHANT/REVIEWER roles, RFC 7807 401/403 | Done |
| 3 | Transaction API: submit/get, Idempotency-Key, correlation id, problem-detail mapping, publish `txn.submitted` | Planned |
| 4 | Screening worker: watchlist screening, `txn.screening-signal`, consumer idempotency, retry + DLT | Planned |
| 5 | Risk worker: DB-driven rule evaluation, `txn.risk-signal` | Planned |
| 6 | Decision engine and outbox relay: decisions, `txn.decided` via the claim protocol | Planned |
| 7 | Review workflow: REVIEWER endpoints for the manual review queue | Planned |
| 8 | Operational hardening: custom metrics, tracing, DLT inspection, CI | Planned |

Nothing from phases 3-8 exists yet: no transaction or review endpoints, services, Kafka producers or
listeners, outbox relay, rule evaluation or correlation id filter. The "Architecture" section describes
that target design.

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
cp .env.example .env               # then set JWT_SECRET, e.g. openssl rand -base64 48
docker compose up --build          # postgres, redis, kafka and the app
curl localhost:8080/actuator/health
curl localhost:8080/actuator/prometheus
```

Run the app from the IDE/host against the compose infrastructure instead:

```bash
docker compose up -d postgres redis kafka
JWT_SECRET=... ./mvnw spring-boot:run   # the Maven plugin activates the "local" profile
```

Host ports: app 8080, Postgres **5433** (avoids clashing with a local Postgres on 5432), Redis 6379,
Kafka 9094 (EXTERNAL listener). Inside the compose network Kafka is `kafka:9092`. All host ports are
overridable (`POSTGRES_HOST_PORT`, `REDIS_HOST_PORT`, `KAFKA_HOST_PORT`, `APP_HOST_PORT`).
docker-compose interpolates `JWT_SECRET` with `:?`, so **every** compose command (including `exec`, `logs`,
`down`) fails without it; keep it in `.env` rather than exporting it per shell.

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
- **There is no default active profile.** The jar and Docker image run the base configuration unless
  `SPRING_PROFILES_ACTIVE` is set; `spring-boot:run` uses `local` via the Maven plugin; compose sets `local`.
- Every value is read through `${ENV_VAR:default}`. The base section has **no defaults for credentials
  or hosts** (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS`); only
  the `local` profile supplies throwaway values matching docker-compose.
- The `test` profile gets connection details from Testcontainers `@ServiceConnection`.
- Never commit real secrets. `.env` files are git-ignored.
- `JWT_SECRET` has **no default in any profile or file** (tests use a random per-context value from
  `src/test/resources/application-test.yml`). Startup fails if it is unset or shorter than 32 bytes.
- Flyway locations: base `classpath:db/migration`; `local` and `test` add `classpath:db/seed-dev` (demo users).
- Actuator exposes `health`, `info`, `prometheus`. Health and prometheus are unauthenticated (see Security).

## Persistence

Schema lives in `src/main/resources/db/migration`: V1-V4 create tables, V5-V7 seed reference data, V8 creates
users. Dev-only data lives in `src/main/resources/db/seed-dev` (V8_1 demo users); use `V<n>_<m>` versions there
so they never take a number the main migrations need.

| Table | Purpose | Key constraints and indexes |
|-------|---------|-----------------------------|
| `transactions` | Submitted payments, `status` PENDING until decided | `(merchant_id, created_at)`, `(payer_account_id, created_at)` for velocity; `@Version` |
| `idempotency_records` | Request idempotency, replayable response | **unique `(idempotency_key, merchant_id)`**; `expires_at` for the sweep; `@Version` |
| `screening_signals` / `risk_signals` | One signal of each kind per transaction | unique `transaction_id`, unique `event_id` |
| `decisions` | One decision per transaction (`TransactionDecision` entity) | unique `transaction_id`; `(decision, decided_at)` review queue |
| `risk_rules` | Rules, weights, thresholds, JSON `params`, decision cut-offs | unique `code`; cut-off rows have `decision` + `threshold` and no `signal_type` (check constraint) |
| `watchlist_entries` | Screening list (seeded list is synthetic) | unique `(list_source, source_reference)`; partial index on `normalized_name` where active |
| `country_risk` | Country risk level and score (seeded values are illustrative) | PK `country_code` |
| `outbox_events` | Transactional outbox with claim protocol | partial indexes for due `PENDING` rows and `CLAIMED` rows |
| `processed_events` | Consumer idempotency | **unique `(consumer_group, event_id)`**; `processed_at` for the sweep |

Rules for schema and entity changes:

- Enum-backed columns are `VARCHAR` + `CHECK`. Adding an enum constant needs a migration that replaces the
  constraint; `SchemaConstraintsTest` fails if an enum and its constraint drift apart.
- Every table has `created_at`/`updated_at TIMESTAMPTZ NOT NULL`. Entities get them from `AuditedEntity`
  (Hibernate timestamps); native `UPDATE`s must set `updated_at` themselves.
- **Entities reference each other by id (UUID/Long columns with DB foreign keys), never with JPA
  associations.** Lombok on entities is limited to `@Getter` and a protected `@NoArgsConstructor`; if an
  association is ever introduced, that entity loses Lombok (see Conventions).
- Application-assigned UUID ids extend `AssignedIdEntity` (`Persistable`, so `save()` inserts without a
  select). Reference data entities (`RiskRule`, `WatchlistEntry`, `CountryRisk`) are `@Immutable`: change
  them with migrations.
- `watchlist_entries.normalized_name` must equal `NameNormalizer.normalize(full_name)`; `SeedDataTest`
  checks this for every seeded row.
- Money is `NUMERIC(19,4)`; amount rules are per currency (no FX conversion).

### Outbox claim protocol

`OutboxEventRepository` implements it; the SQL is documented in V4.

1. `claimBatch(owner, now, staleBefore, batchSize)` in its own short transaction: atomically moves due
   `PENDING` rows (and `CLAIMED` rows whose `claimed_at` is older than the claim timeout) to `CLAIMED`, sets
   `claimed_by`/`claimed_at`, increments `attempts`. `FOR UPDATE SKIP LOCKED` guarantees concurrent relays
   get disjoint rows (`OutboxClaimConcurrencyTest`).
2. Publish to Kafka outside any database transaction.
3. Report with `markPublished`, `markForRetry` (back to `PENDING` with a later `next_attempt_at`) or
   `markFailed`. All three require `status = CLAIMED AND claimed_by = owner` and return rows updated, so a
   relay whose claim went stale and was taken over gets 0 and must not act further.

Reclaiming stale claims means a relay that crashed after sending but before `markPublished` causes a
re-publish: delivery is at-least-once and `txn.decided` consumers dedupe by event id (the outbox row id).

### Consumer idempotency

Call `ProcessedEventRepository.markProcessed(group, eventId, topic, now)` first inside the consumer's
transaction. It uses `INSERT ... ON CONFLICT DO NOTHING` and returns 0 for a duplicate, so skip the side
effects when it does; it never throws on duplicates and does not poison the transaction.

### Repository tests

Annotate with `@RepositoryTest` (`@DataJpaTest` + real PostgreSQL via `PostgresTestcontainersConfiguration`,
all migrations and seeds applied, one shared container). Tests roll back by default; tests that need real
commits (concurrency) use `@Transactional(propagation = NOT_SUPPORTED)` and clean up after themselves.
Seed data is shared by all tests, so never modify seeded rows in a committing test.

## Security

- Stateless `SecurityFilterChain` (`config/SecurityConfig`), OAuth2 resource server with HS256 JWTs signed
  and verified with `JWT_SECRET` (`config/JwtConfig`). No sessions, CSRF, form login or HTTP basic.
- `POST /api/v1/auth/login` (`api/AuthController` -> `security/LoginService`) checks the BCrypt hash via a
  `DaoAuthenticationProvider` and returns `{accessToken, tokenType, expiresIn, expiresAt, roles}` with
  `Cache-Control: no-store`. `GET /api/v1/auth/me` echoes the caller's claims.
- Token claims: `sub` = username, `iss`, `iat`, `exp`, `jti`, `roles` (e.g. `["MERCHANT"]`), `merchant_id`
  for merchant users. The decoder requires our issuer, HS256, a valid signature and a present, unexpired `exp`
  (60 s clock skew). TTL is `JWT_ACCESS_TOKEN_TTL` (default 15 minutes). There are no refresh tokens.
- Roles: `MERCHANT`, `REVIEWER` (`domain/Role`, `user_roles` table). The `roles` claim becomes `ROLE_*`
  authorities. Route rules: `/api/v1/transactions/**` MERCHANT, `/api/v1/reviews/**` REVIEWER, login and
  health/prometheus public, everything else authenticated. Add new routes to these rules, not ad hoc.
- Errors are RFC 7807: 401 (missing/invalid/expired token, or failed login) and 403 (wrong role) come from
  `security/ProblemDetailsAuthenticationEntryPoint` and `ProblemDetailsAccessDeniedHandler`, which keep the
  RFC 6750 `WWW-Authenticate` header; controller errors go through `common/ApiExceptionHandler`.
- Failed logins return the same body for unknown users and wrong passwords.
- Users: `app_users` + `user_roles` (V8). Usernames are lower case (login lower-cases input). Demo users
  exist only where `db/seed-dev` is loaded: `merchant.demo` / `merchant-dev-password` (merchant
  `merchant-demo`) and `reviewer.demo` / `reviewer-dev-password`. There is no user management API yet.
- Integration tests use `@IntegrationTest` (full app, MockMvc, shared Testcontainers context).

## Package layout (`com.ritikasharma.risk`)

| Package     | Responsibility |
|-------------|----------------|
| `api`       | REST controllers and request/response DTOs (auth today; Idempotency-Key handling in phase 3) |
| `domain`    | Entities, repositories, enums (see Persistence) |
| `screening` | Screening worker: consumes `txn.submitted`, emits `txn.screening-signal` |
| `risk`      | Risk worker: consumes `txn.submitted`, evaluates DB-driven rules, emits `txn.risk-signal` |
| `decision`  | Decision engine: joins both signals, decides APPROVE / REVIEW / BLOCK, writes outbox |
| `messaging` | Topic names (`Topics`), producers, outbox relay, processed-event store, retry/DLT wiring |
| `security`  | JWT login, user details, 401/403 problem-detail handlers |
| `config`    | Spring configuration (security filter chain, JWT encoder/decoder, clock, Kafka topics) |
| `common`    | RFC 7807 problem details (`ApiExceptionHandler`, `ProblemDetails`), shared utilities (`NameNormalizer`), later the correlation id |

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
  on associations cause lazy-loading and recursion bugs. Write those entities by hand. Current entities have
  no associations and use only `@Getter` + protected `@NoArgsConstructor`; never `@Data`, `@EqualsAndHashCode`
  or `@ToString` on an entity.
- Kafka messages are keyed by transaction id.
- Store money as `BigDecimal` / `NUMERIC` with an ISO 4217 currency code, and timestamps as `Instant` / `TIMESTAMPTZ` in UTC.
- Integration tests use Testcontainers via `TestcontainersConfiguration`, not mocks of Kafka or Postgres.
  Use Awaitility for async assertions and never `Thread.sleep`.
