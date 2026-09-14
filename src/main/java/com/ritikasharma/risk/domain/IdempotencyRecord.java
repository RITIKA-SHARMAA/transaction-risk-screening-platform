package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord extends AssignedIdEntity {

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private String merchantId;

    /** Lower-case hex SHA-256 of the canonical request body. */
    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    /** Raw JSON response body replayed for repeated requests. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public IdempotencyRecord(UUID id, String idempotencyKey, String merchantId, String requestHash, Instant expiresAt) {
        super(id);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    public boolean matchesRequest(String otherRequestHash) {
        return requestHash.equals(otherRequestHash);
    }

    public void complete(int responseStatus, String responseBody, UUID transactionId) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.transactionId = transactionId;
    }
}
