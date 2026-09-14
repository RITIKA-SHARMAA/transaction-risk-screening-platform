package com.ritikasharma.risk.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionDecisionRepository extends JpaRepository<TransactionDecision, UUID> {

    Optional<TransactionDecision> findByTransactionId(UUID transactionId);

    /** Oldest decisions of one outcome first, e.g. the manual review queue. */
    List<TransactionDecision> findByDecisionOrderByDecidedAtAsc(Decision decision, Limit limit);
}
