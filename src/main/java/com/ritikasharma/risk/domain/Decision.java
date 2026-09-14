package com.ritikasharma.risk.domain;

/** Outcome of the decision engine. Mirrors ck_decisions_decision and ck_risk_rules_decision. */
public enum Decision {
    APPROVE,
    REVIEW,
    BLOCK
}
