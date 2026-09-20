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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "screening_signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreeningSignal extends AssignedIdEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "hit", nullable = false, updatable = false)
    private boolean hit;

    @Column(name = "match_score", nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal matchScore;

    @Column(name = "matched_entry_id", updatable = false)
    private Long matchedEntryId;

    @Column(name = "matched_name", updatable = false)
    private String matchedName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, updatable = false)
    private Map<String, Object> details;

    private ScreeningSignal(UUID id, UUID transactionId, UUID eventId, boolean hit, BigDecimal matchScore,
                            Long matchedEntryId, String matchedName, Map<String, Object> details) {
        super(id);
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.hit = hit;
        this.matchScore = Objects.requireNonNull(matchScore, "matchScore");
        this.matchedEntryId = matchedEntryId;
        this.matchedName = matchedName;
        this.details = Map.copyOf(details);
    }

    public static ScreeningSignal clear(UUID id, UUID transactionId, UUID eventId, BigDecimal bestMatchScore,
                                        Map<String, Object> details) {
        return new ScreeningSignal(id, transactionId, eventId, false, bestMatchScore, null, null, details);
    }

    public static ScreeningSignal hit(UUID id, UUID transactionId, UUID eventId, BigDecimal matchScore,
                                      long matchedEntryId, String matchedName, Map<String, Object> details) {
        return new ScreeningSignal(id, transactionId, eventId, true, matchScore,
                matchedEntryId, Objects.requireNonNull(matchedName, "matchedName"), details);
    }
}
