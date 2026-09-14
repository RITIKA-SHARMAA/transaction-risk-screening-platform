package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

/** Rule configuration, maintained through Flyway migrations. Read-only to the application. */
@Entity
@Table(name = "risk_rules")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskRule extends AuditedEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    /** Null for {@link RuleType#DECISION_CUTOFF} rows. */
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type")
    private SignalType signalType;

    /** Outcome reached at or above {@link #threshold}; only set for {@link RuleType#DECISION_CUTOFF} rows. */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private Decision decision;

    @Column(name = "weight", nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "threshold", precision = 19, scale = 4)
    private BigDecimal threshold;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false)
    private Map<String, Object> params;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
