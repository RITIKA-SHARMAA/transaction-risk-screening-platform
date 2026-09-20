package com.ritikasharma.risk.domain;

import java.math.BigDecimal;

/** One rule that fired while scoring a transaction; stored in {@code risk_signals.triggered_rules}. */
public record TriggeredRule(String code, RuleType ruleType, BigDecimal weight) {
}
