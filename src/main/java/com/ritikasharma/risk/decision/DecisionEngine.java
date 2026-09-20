package com.ritikasharma.risk.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritikasharma.risk.domain.*;
import com.ritikasharma.risk.messaging.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class DecisionEngine {
    static final String GROUP = "risk-decision-engine";

    private final ObjectMapper mapper;
    private final ProcessedEventRepository processed;
    private final ScreeningSignalRepository screeningSignals;
    private final RiskSignalRepository riskSignals;
    private final TransactionRepository transactions;
    private final TransactionDecisionRepository decisions;
    private final RiskRuleRepository rules;
    private final EventPublisher events;
    private final Clock clock;

    public DecisionEngine(ObjectMapper mapper, ProcessedEventRepository processed,
                          ScreeningSignalRepository screeningSignals, RiskSignalRepository riskSignals,
                          TransactionRepository transactions, TransactionDecisionRepository decisions,
                          RiskRuleRepository rules, EventPublisher events, Clock clock) {
        this.mapper = mapper;
        this.processed = processed;
        this.screeningSignals = screeningSignals;
        this.riskSignals = riskSignals;
        this.transactions = transactions;
        this.decisions = decisions;
        this.rules = rules;
        this.events = events;
        this.clock = clock;
    }

    @KafkaListener(topics = {Topics.TXN_SCREENING_SIGNAL, Topics.TXN_RISK_SIGNAL}, groupId = GROUP)
    @Transactional
    public void consume(String json) {
        EventEnvelope envelope = events.parse(json);
        if (processed.markProcessed(GROUP, envelope.eventId(), envelope.topic(), clock.instant()) == 0) return;

        UUID transactionId = envelope.aggregateId();
        if (decisions.findByTransactionId(transactionId).isPresent()) return;

        if (screeningSignals.findByTransactionId(transactionId).isEmpty()
                || riskSignals.findByTransactionId(transactionId).isEmpty()) return;

        Transaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + transactionId));
        ScreeningSignal screening = screeningSignals.findByTransactionId(transactionId).orElseThrow();
        RiskSignal risk = riskSignals.findByTransactionId(transactionId).orElseThrow();

        Decision decision = choose(risk.getScore(), screening.isHit());
        List<String> reasons = new ArrayList<>();
        risk.getTriggeredRules().forEach(rule -> reasons.add(rule.code()));
        if (screening.isHit()) reasons.add("WATCHLIST_NAME_MATCH");

        Instant now = clock.instant();
        TransactionDecision result = new TransactionDecision(UUID.randomUUID(), transactionId, decision,
                risk.getScore(), screening.isHit(), reasons, now);
        tx.applyDecision(decision);
        decisions.save(result);
        events.enqueue("TRANSACTION", transactionId, "TransactionDecided", Topics.TXN_DECIDED,
                new TransactionDecided(transactionId, decision, risk.getScore(), screening.isHit(), reasons, now),
                envelope.correlationId());
    }

    private Decision choose(BigDecimal score, boolean screeningHit) {
        if (screeningHit) return Decision.BLOCK;
        for (RiskRule rule : rules.findEnabledDecisionCutoffs()) {
            if (score.compareTo(rule.getThreshold()) >= 0) return rule.getDecision();
        }
        return Decision.APPROVE;
    }
}
