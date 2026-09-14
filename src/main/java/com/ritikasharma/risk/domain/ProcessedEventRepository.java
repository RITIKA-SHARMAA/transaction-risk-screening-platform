package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByConsumerGroupAndEventId(String consumerGroup, UUID eventId);

    /**
     * Records that {@code consumerGroup} processed {@code eventId}. Returns 1 the first time and 0 for a
     * duplicate, without raising, so a consumer can call it first inside its transaction and skip the
     * side effects when it returns 0. A concurrent duplicate blocks until the first transaction finishes.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO processed_events (consumer_group, event_id, topic, processed_at, created_at, updated_at)
            VALUES (:consumerGroup, :eventId, :topic, :now, :now, :now)
            ON CONFLICT (consumer_group, event_id) DO NOTHING
            """, nativeQuery = true)
    int markProcessed(@Param("consumerGroup") String consumerGroup, @Param("eventId") UUID eventId,
                      @Param("topic") String topic, @Param("now") Instant now);

    /** Retention sweep. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProcessedEvent p where p.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
