package com.ritikasharma.risk.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionDecisionRepository extends JpaRepository<TransactionDecision, UUID> {
    Optional<TransactionDecision> findByTransactionId(UUID transactionId);

    List<TransactionDecision> findByDecisionOrderByDecidedAtAsc(Decision decision, Limit limit);

    @Query("select d from TransactionDecision d where d.decision = :decision " +
           "and not exists (select a from ReviewAction a where a.transactionId = d.transactionId) " +
           "order by d.decidedAt asc")
    List<TransactionDecision> findOpenReviews(Decision decision, Limit limit);
}
