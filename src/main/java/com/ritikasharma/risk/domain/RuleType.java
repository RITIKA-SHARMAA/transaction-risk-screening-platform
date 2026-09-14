package com.ritikasharma.risk.domain;

/**
 * How a rule row is evaluated. Only the evaluation strategy lives in code; weights, thresholds and
 * parameters come from {@code risk_rules}. Mirrors ck_risk_rules_rule_type.
 */
public enum RuleType {
    AMOUNT_THRESHOLD,
    VELOCITY,
    COUNTRY_RISK,
    CROSS_BORDER,
    WATCHLIST_MATCH,
    DECISION_CUTOFF
}
