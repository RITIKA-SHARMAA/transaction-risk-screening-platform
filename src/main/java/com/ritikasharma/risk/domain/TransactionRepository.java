package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Merchant-scoped lookup, so one merchant can never read another merchant's transaction. */
    Optional<Transaction> findByIdAndMerchantId(UUID id, String merchantId);

    /** Velocity rules: transactions for a payer account created at or after {@code since}. */
    @Query("select count(t) from Transaction t where t.payerAccountId = :payerAccountId and t.createdAt >= :since")
    long countByPayerAccountSince(@Param("payerAccountId") String payerAccountId, @Param("since") Instant since);
}
