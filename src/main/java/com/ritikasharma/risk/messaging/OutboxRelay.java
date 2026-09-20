package com.ritikasharma.risk.messaging;

import com.ritikasharma.risk.domain.OutboxEvent;
import com.ritikasharma.risk.domain.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration staleAfter;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafka, Clock clock,
                       MeterRegistry metrics,
                       @Value("${risk.outbox.batch-size:50}") int batchSize,
                       @Value("${risk.outbox.max-attempts:8}") int maxAttempts,
                       @Value("${risk.outbox.stale-after:PT2M}") Duration staleAfter) {
        this.repository = repository;
        this.kafka = kafka;
        this.clock = clock;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${risk.outbox.poll-delay:1000}")
    public void relay() {
        Instant now = clock.instant();
        String owner = UUID.randomUUID().toString();
        List<OutboxEvent> events = repository.claimBatch(owner, now, now.minus(staleAfter), batchSize);
        for (OutboxEvent event : events) publish(event, owner);
    }

    private void publish(OutboxEvent event, String owner) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    event.getTopic(), null, event.getAggregateId().toString(), event.getPayload());
            record.headers().add(new RecordHeader("event-id",
                    event.getId().toString().getBytes(StandardCharsets.UTF_8)));
            if (event.getCorrelationId() != null)
                record.headers().add(new RecordHeader("X-Correlation-Id",
                        event.getCorrelationId().getBytes(StandardCharsets.UTF_8)));
            kafka.send(record).get(10, TimeUnit.SECONDS);
            repository.markPublished(event.getId(), owner, clock.instant());
            metrics.counter("risk.outbox.published").increment();
        } catch (Exception ex) {
            Instant now = clock.instant();
            String error = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            if (error.length() > 2000) error = error.substring(0, 2000);
            if (event.getAttempts() >= maxAttempts)
                repository.markFailed(event.getId(), owner, error);
            else {
                long seconds = Math.min(60, 1L << Math.min(event.getAttempts(), 6));
                repository.markForRetry(event.getId(), owner, now.plusSeconds(seconds), error, now);
            }
            metrics.counter("risk.outbox.failures").increment();
        }
    }
}
