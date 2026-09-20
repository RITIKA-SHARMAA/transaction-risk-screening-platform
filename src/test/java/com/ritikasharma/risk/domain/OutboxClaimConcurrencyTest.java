package com.ritikasharma.risk.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two relays claim concurrently with real, committed transactions. Relay A keeps its transaction (and row
 * locks) open while relay B claims; without SKIP LOCKED, B would block on A's rows and this test would time out.
 */
@RepositoryTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxClaimConcurrencyTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant STALE_BEFORE = NOW.minus(Duration.ofMinutes(5));

    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
        repository.saveAll(IntStream.range(0, 10).mapToObj(i -> TestData.outboxEvent(NOW)).toList());
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        repository.deleteAllInBatch();
    }

    @Test
    void concurrentRelaysClaimDisjointBatches() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch relayAClaimed = new CountDownLatch(1);
        CountDownLatch relayBFinished = new CountDownLatch(1);

        CompletableFuture<List<UUID>> relayA = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            List<UUID> ids = ids(repository.claimBatch("relay-a", NOW, STALE_BEFORE, 6));
            relayAClaimed.countDown();
            await(relayBFinished);   // hold A's row locks until B has claimed
            return ids;
        }), executor);

        CompletableFuture<List<UUID>> relayB = CompletableFuture.supplyAsync(() -> {
            await(relayAClaimed);
            try {
                return tx.execute(status -> ids(repository.claimBatch("relay-b", NOW, STALE_BEFORE, 6)));
            } finally {
                relayBFinished.countDown();
            }
        }, executor);

        List<UUID> claimedByA = relayA.get(30, TimeUnit.SECONDS);
        List<UUID> claimedByB = relayB.get(30, TimeUnit.SECONDS);

        assertThat(claimedByA).hasSize(6);
        assertThat(claimedByB).hasSize(4);
        assertThat(claimedByA).doesNotContainAnyElementsOf(claimedByB);
        Set<UUID> all = new HashSet<>(claimedByA);
        all.addAll(claimedByB);
        assertThat(all).hasSize(10);

        assertThat(repository.findAll()).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.CLAIMED);
            assertThat(event.getAttempts()).isEqualTo(1);
            assertThat(event.getClaimedBy()).isEqualTo(claimedByA.contains(event.getId()) ? "relay-a" : "relay-b");
        });
    }

    private static List<UUID> ids(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::getId).toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other relay");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
