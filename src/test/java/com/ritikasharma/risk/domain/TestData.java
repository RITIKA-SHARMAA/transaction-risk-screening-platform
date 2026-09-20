package com.ritikasharma.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class TestData {

    static final String MERCHANT = "merchant-1";

    private TestData() {
    }

    static Transaction transaction() {
        return transaction(MERCHANT, "payer-" + UUID.randomUUID());
    }

    static Transaction transaction(String merchantId, String payerAccountId) {
        return new Transaction(UUID.randomUUID(), merchantId, new BigDecimal("125.50"), "USD",
                payerAccountId, "Jane Payer", "US", "Acme Supplies", "GB", "corr-" + UUID.randomUUID());
    }

    static IdempotencyRecord idempotencyRecord(String key, String merchantId, Instant expiresAt) {
        return new IdempotencyRecord(UUID.randomUUID(), key, merchantId, "a".repeat(64), expiresAt);
    }

    static OutboxEvent outboxEvent(Instant availableAt) {
        UUID aggregateId = UUID.randomUUID();
        return new OutboxEvent(UUID.randomUUID(), "Transaction", aggregateId, "TransactionDecided", "txn.decided",
                "{\"transactionId\":\"" + aggregateId + "\",\"decision\":\"APPROVE\"}", "corr-1", availableAt);
    }
}
