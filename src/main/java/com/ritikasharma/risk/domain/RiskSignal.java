package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "risk_signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskSignal extends AssignedIdEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "score", nullable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_rules", nullable = false, updatable = false)
    private List<TriggeredRule> triggeredRules;

    public RiskSignal(UUID id, UUID transactionId, UUID eventId, BigDecimal score, List<TriggeredRule> triggeredRules) {
        super(id);
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.score = Objects.requireNonNull(score, "score");
        this.triggeredRules = List.copyOf(triggeredRules);
    }
}
