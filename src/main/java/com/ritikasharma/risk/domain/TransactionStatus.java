package com.ritikasharma.risk.domain;

/** Mirrors ck_transactions_status. */
public enum TransactionStatus {
    PENDING,
    APPROVED,
    IN_REVIEW,
    BLOCKED,
    FAILED;

    public static TransactionStatus of(Decision decision) {
        return switch (decision) {
            case APPROVE -> APPROVED;
            case REVIEW -> IN_REVIEW;
            case BLOCK -> BLOCKED;
        };
    }
}
