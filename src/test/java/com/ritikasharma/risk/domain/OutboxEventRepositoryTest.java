package com.ritikasharma.risk.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class OutboxEventRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void newEventIsPendingAndStoresJsonPayload() {
        OutboxEvent event = repository.saveAndFlush(TestData.outboxEvent(NOW));
        entityManager.clear();

        OutboxEvent loaded = repository.findById(event.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(loaded.getAttempts()).isZero();
        assertThat(loaded.getClaimedBy()).isNull();
        assertThat(loaded.getPayload()).contains("\"decision\": \"APPROVE\"");
    }

    @Test
    void claimBatchClaimsDuePendingEventsInCreationOrderUpToBatchSize() {
        OutboxEvent first = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(30));
        OutboxEvent second = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(20));
        OutboxEvent third = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(10));

        List<OutboxEvent> claimed = claim("relay-a", NOW, 2);

        assertThat(claimed).extracting(OutboxEvent::getId).containsExactly(first.getId(), second.getId());
        assertThat(claimed).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(OutboxStatus.CLAIMED);
            assertThat(e.getClaimedBy()).isEqualTo("relay-a");
            assertThat(e.getClaimedAt()).isEqualTo(NOW);
            assertThat(e.getAttempts()).isEqualTo(1);
        });
        assertThat(statusOf(third.getId())).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void claimBatchSkipsEventsNotYetDueAndFreshClaims() {
        OutboxEvent notDue = saveCreatedAt(TestData.outboxEvent(NOW.plusSeconds(60)), NOW.minusSeconds(30));
        saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(20));
        assertThat(claim("relay-a", NOW, 10)).hasSize(1);

        // A minute later: the claim is fresh (younger than the timeout) and the other event is now due.
        List<OutboxEvent> claimed = claim("relay-b", NOW.plusSeconds(60), 10);

        assertThat(claimed).extracting(OutboxEvent::getId).containsExactly(notDue.getId());
    }

    @Test
    void claimBatchReclaimsStaleClaimsAndIncrementsAttempts() {
        OutboxEvent event = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(10));
        claim("relay-crashed", NOW, 10);

        Instant later = NOW.plus(CLAIM_TIMEOUT).plusSeconds(1);
        List<OutboxEvent> reclaimed = claim("relay-b", later, 10);

        assertThat(reclaimed).singleElement().satisfies(e -> {
            assertThat(e.getId()).isEqualTo(event.getId());
            assertThat(e.getClaimedBy()).isEqualTo("relay-b");
            assertThat(e.getClaimedAt()).isEqualTo(later);
            assertThat(e.getAttempts()).isEqualTo(2);
        });
    }

    @Test
    void markPublishedIsGuardedByClaimOwner() {
        OutboxEvent event = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(10));
        claim("relay-a", NOW, 10);

        assertThat(repository.markPublished(event.getId(), "relay-b", NOW)).isZero();
        assertThat(statusOf(event.getId())).isEqualTo(OutboxStatus.CLAIMED);

        Instant publishedAt = NOW.plusSeconds(1);
        assertThat(repository.markPublished(event.getId(), "relay-a", publishedAt)).isEqualTo(1);
        OutboxEvent published = repository.findById(event.getId()).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isEqualTo(publishedAt);

        assertThat(repository.markPublished(event.getId(), "relay-a", publishedAt)).isZero();
        assertThat(claim("relay-c", NOW.plus(Duration.ofDays(1)), 10)).isEmpty();
    }

    @Test
    void staleOwnerCannotPublishAfterItsClaimWasTakenOver() {
        OutboxEvent event = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(10));
        claim("relay-a", NOW, 10);
        claim("relay-b", NOW.plus(CLAIM_TIMEOUT).plusSeconds(1), 10);

        assertThat(repository.markPublished(event.getId(), "relay-a", NOW)).isZero();
        assertThat(repository.findById(event.getId()).orElseThrow().getClaimedBy()).isEqualTo("relay-b");
    }

    @Test
    void markForRetryReleasesClaimAndDefersNextAttempt() {
        OutboxEvent event = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(10));
        claim("relay-a", NOW, 10);
        Instant retryAt = NOW.plusSeconds(30);

        assertThat(repository.markForRetry(event.getId(), "relay-b", retryAt, "boom", NOW)).isZero();
        assertThat(repository.markForRetry(event.getId(), "relay-a", retryAt, "broker unavailable", NOW)).isEqualTo(1);

        OutboxEvent retried = repository.findById(event.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(retried.getClaimedBy()).isNull();
        assertThat(retried.getClaimedAt()).isNull();
        assertThat(retried.getNextAttemptAt()).isEqualTo(retryAt);
        assertThat(retried.getLastError()).isEqualTo("broker unavailable");
        assertThat(retried.getAttempts()).isEqualTo(1);
        entityManager.clear();

        assertThat(claim("relay-a", NOW.plusSeconds(29), 10)).isEmpty();
        assertThat(claim("relay-a", retryAt, 10)).singleElement()
                .extracting(OutboxEvent::getAttempts).isEqualTo(2);
    }

    @Test
    void markFailedStopsFurtherClaims() {
        OutboxEvent event = saveCreatedAt(TestData.outboxEvent(NOW), NOW.minusSeconds(10));
        claim("relay-a", NOW, 10);

        assertThat(repository.markFailed(event.getId(), "relay-b", "nope", NOW)).isZero();
        assertThat(repository.markFailed(event.getId(), "relay-a", "payload rejected", NOW)).isEqualTo(1);

        OutboxEvent failed = repository.findById(event.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getLastError()).isEqualTo("payload rejected");
        assertThat(claim("relay-a", NOW.plus(Duration.ofDays(1)), 10)).isEmpty();
    }

    private List<OutboxEvent> claim(String owner, Instant now, int batchSize) {
        entityManager.clear();
        List<OutboxEvent> claimed = repository.claimBatch(owner, now, now.minus(CLAIM_TIMEOUT), batchSize);
        entityManager.clear();
        return claimed;
    }

    private OutboxEvent saveCreatedAt(OutboxEvent event, Instant createdAt) {
        repository.saveAndFlush(event);
        jdbc.update("UPDATE outbox_events SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), event.getId());
        return event;
    }

    private OutboxStatus statusOf(UUID id) {
        entityManager.clear();
        return repository.findById(id).orElseThrow().getStatus();
    }
}
