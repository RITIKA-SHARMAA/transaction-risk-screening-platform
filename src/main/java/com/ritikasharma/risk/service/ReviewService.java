package com.ritikasharma.risk.service;

import com.ritikasharma.risk.api.ReviewItemResponse;
import com.ritikasharma.risk.api.ReviewRequest;
import com.ritikasharma.risk.common.IdempotencyConflictException;
import com.ritikasharma.risk.common.ResourceNotFoundException;
import com.ritikasharma.risk.domain.*;
import com.ritikasharma.risk.messaging.EventPublisher;
import com.ritikasharma.risk.messaging.Topics;
import com.ritikasharma.risk.messaging.TransactionDecided;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {
    private final TransactionRepository transactions;
    private final TransactionDecisionRepository decisions;
    private final ReviewActionRepository actions;
    private final EventPublisher events;
    private final Clock clock;

    public ReviewService(TransactionRepository transactions, TransactionDecisionRepository decisions,
                         ReviewActionRepository actions, EventPublisher events, Clock clock) {
        this.transactions = transactions;
        this.decisions = decisions;
        this.actions = actions;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReviewItemResponse> queue(int limit) {
        return decisions.findOpenReviews(Decision.REVIEW, Limit.of(limit)).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReviewItemResponse get(UUID transactionId) {
        return decisions.findByTransactionId(transactionId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Review item not found."));
    }

    @Transactional
    public ReviewItemResponse decide(UUID transactionId, ReviewRequest request, String reviewer) {
        if (request.decision() == Decision.REVIEW)
            throw new IdempotencyConflictException("Review decision must be APPROVE or BLOCK.");
        Transaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
        TransactionDecision decision = decisions.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Review item not found."));
        if (decision.getDecision() != Decision.REVIEW)
            throw new IdempotencyConflictException("Transaction is not in the manual review queue.");
        if (actions.findByTransactionId(transactionId).isPresent())
            throw new IdempotencyConflictException("This review has already been completed.");

        tx.applyReviewDecision(request.decision());
        var action = new ReviewAction(UUID.randomUUID(), transactionId, reviewer, request.decision(),
                request.comment(), clock.instant());
        actions.save(action);
        events.enqueue("TRANSACTION", transactionId, "TransactionReviewCompleted", Topics.TXN_DECIDED,
                new TransactionDecided(transactionId, request.decision(), decision.getRiskScore(),
                        decision.isScreeningHit(), List.of("MANUAL_REVIEW:" + reviewer), action.getReviewedAt()),
                tx.getCorrelationId());
        return toResponse(decision);
    }

    private ReviewItemResponse toResponse(TransactionDecision decision) {
        Transaction tx = transactions.findById(decision.getTransactionId()).orElseThrow();
        return new ReviewItemResponse(tx.getId(), tx.getMerchantId(), tx.getAmount(), tx.getCurrency(),
                tx.getPayerName(), tx.getPayeeName(), tx.getPayerCountry(), tx.getPayeeCountry(),
                tx.getStatus(), decision.getDecision(), decision.getRiskScore(), decision.isScreeningHit(),
                decision.getDecidedAt());
    }
}
