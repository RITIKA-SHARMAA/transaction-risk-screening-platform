package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndMerchantId(String idempotencyKey, String merchantId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO idempotency_records
                (id, idempotency_key, merchant_id, request_hash, status, expires_at, version, created_at, updated_at)
            VALUES (:id, :key, :merchantId, :requestHash, 'IN_PROGRESS', :expiresAt, 0, :now, :now)
            ON CONFLICT (idempotency_key, merchant_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("key") String key, @Param("merchantId") String merchantId,
                       @Param("requestHash") String requestHash, @Param("expiresAt") Instant expiresAt,
                       @Param("now") Instant now);

    /** Retention sweep. Returns the number of records deleted. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
