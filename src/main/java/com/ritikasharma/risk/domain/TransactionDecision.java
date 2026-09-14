package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Row in {@code decisions}; named to avoid clashing with the {@link Decision} outcome enum. */
@Entity
@Table(name = "decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionDecision extends AssignedIdEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false)
    private Decision decision;

    @Column(name = "risk_score", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "screening_hit", nullable = false, updatable = false)
    private boolean screeningHit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, updatable = false)
    private List<String> reasons;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    public TransactionDecision(UUID id, UUID transactionId, Decision decision, BigDecimal riskScore,
                               boolean screeningHit, List<String> reasons, Instant decidedAt) {
        super(id);
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.riskScore = Objects.requireNonNull(riskScore, "riskScore");
        this.screeningHit = screeningHit;
        this.reasons = List.copyOf(reasons);
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
