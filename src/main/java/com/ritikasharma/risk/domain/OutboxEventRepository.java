package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox access for the relay. The claim protocol is described in the V4 migration: claim a batch in a
 * short transaction, publish outside it, then report the outcome with one of the {@code mark*} methods,
 * each guarded by {@code claimed_by} so an instance whose claim went stale and was taken over cannot
 * overwrite the new owner's state.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Atomically claims up to {@code batchSize} publishable events for {@code owner}: pending events that are
     * due, plus claims older than {@code staleBefore}. Increments {@code attempts}. Rows locked by a concurrent
     * claimer are skipped, so two relays never receive the same row. Results are ordered by creation time.
     *
     * <p>Call it in a transaction that has not loaded these rows yet; already-managed instances would be
     * returned with their stale in-memory state.
     */
    @Transactional
    @Query(value = """
            WITH claimed AS (
                UPDATE outbox_events
                   SET status = 'CLAIMED',
                       claimed_by = :owner,
                       claimed_at = :now,
                       attempts = attempts + 1,
                       updated_at = :now
                 WHERE id IN (
                       SELECT id
                         FROM outbox_events
                        WHERE (status = 'PENDING' AND next_attempt_at <= :now)
                           OR (status = 'CLAIMED' AND claimed_at < :staleBefore)
                        ORDER BY created_at
                        LIMIT :batchSize
                          FOR UPDATE SKIP LOCKED)
             RETURNING *)
            SELECT * FROM claimed ORDER BY created_at
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("owner") String owner,
                                 @Param("now") Instant now,
                                 @Param("staleBefore") Instant staleBefore,
                                 @Param("batchSize") int batchSize);

    /** Returns 1 if the event was still claimed by {@code owner} and is now published, otherwise 0. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE outbox_events
               SET status = 'PUBLISHED', published_at = :now, last_error = NULL, updated_at = :now
             WHERE id = :id AND status = 'CLAIMED' AND claimed_by = :owner
            """, nativeQuery = true)
    int markPublished(@Param("id") UUID id, @Param("owner") String owner, @Param("now") Instant now);

    /** Releases the claim and schedules another attempt at {@code nextAttemptAt}. Returns rows updated (0 or 1). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE outbox_events
               SET status = 'PENDING', claimed_by = NULL, claimed_at = NULL,
                   next_attempt_at = :nextAttemptAt, last_error = :error, updated_at = :now
             WHERE id = :id AND status = 'CLAIMED' AND claimed_by = :owner
            """, nativeQuery = true)
    int markForRetry(@Param("id") UUID id, @Param("owner") String owner, @Param("nextAttemptAt") Instant nextAttemptAt,
                     @Param("error") String error, @Param("now") Instant now);

    /** Gives up on the event (attempts exhausted or non-retryable). Returns rows updated (0 or 1). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE outbox_events
               SET status = 'FAILED', last_error = :error, updated_at = :now
             WHERE id = :id AND status = 'CLAIMED' AND claimed_by = :owner
            """, nativeQuery = true)
    int markFailed(@Param("id") UUID id, @Param("owner") String owner, @Param("error") String error,
                   @Param("now") Instant now);
}
