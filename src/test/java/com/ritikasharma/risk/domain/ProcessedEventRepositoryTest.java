package com.ritikasharma.risk.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class ProcessedEventRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private ProcessedEventRepository repository;

    @Test
    void markProcessedReturnsOneOnFirstDeliveryAndZeroForDuplicates() {
        UUID eventId = UUID.randomUUID();

        assertThat(repository.markProcessed("risk-worker", eventId, "txn.submitted", NOW)).isEqualTo(1);
        assertThat(repository.markProcessed("risk-worker", eventId, "txn.submitted", NOW)).isZero();

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void sameEventIsTrackedIndependentlyPerConsumerGroup() {
        UUID eventId = UUID.randomUUID();

        assertThat(repository.markProcessed("risk-worker", eventId, "txn.submitted", NOW)).isEqualTo(1);
        assertThat(repository.markProcessed("screening-worker", eventId, "txn.submitted", NOW)).isEqualTo(1);

        assertThat(repository.existsByConsumerGroupAndEventId("risk-worker", eventId)).isTrue();
        assertThat(repository.existsByConsumerGroupAndEventId("screening-worker", eventId)).isTrue();
        assertThat(repository.existsByConsumerGroupAndEventId("decision-engine", eventId)).isFalse();
        assertThat(repository.existsByConsumerGroupAndEventId("risk-worker", UUID.randomUUID())).isFalse();
    }

    @Test
    void duplicateInsertThroughEntityViolatesUniqueConstraint() {
        UUID eventId = UUID.randomUUID();
        repository.saveAndFlush(new ProcessedEvent("risk-worker", eventId, "txn.submitted", NOW));

        assertThatThrownBy(() -> repository.saveAndFlush(new ProcessedEvent("risk-worker", eventId, "txn.submitted", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_processed_events_group_event");
    }

    @Test
    void deleteProcessedBeforeRemovesOnlyOlderRows() {
        repository.markProcessed("risk-worker", UUID.randomUUID(), "txn.submitted", NOW.minus(Duration.ofDays(8)));
        UUID recent = UUID.randomUUID();
        repository.markProcessed("risk-worker", recent, "txn.submitted", NOW.minus(Duration.ofDays(1)));

        assertThat(repository.deleteProcessedBefore(NOW.minus(Duration.ofDays(7)))).isEqualTo(1);

        assertThat(repository.findAll()).extracting(ProcessedEvent::getEventId).containsExactly(recent);
    }
}
