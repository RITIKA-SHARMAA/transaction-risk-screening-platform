package com.ritikasharma.risk.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritikasharma.risk.domain.*;
import com.ritikasharma.risk.messaging.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ScreeningWorker {
    static final String GROUP = "risk-screening-worker";

    private final ObjectMapper mapper;
    private final ProcessedEventRepository processed;
    private final TransactionRepository transactions;
    private final ScreeningSignalRepository signals;
    private final RiskRuleRepository rules;
    private final WatchlistEntryRepository watchlist;
    private final EventPublisher events;
    private final Clock clock;

    public ScreeningWorker(ObjectMapper mapper, ProcessedEventRepository processed, TransactionRepository transactions,
                           ScreeningSignalRepository signals, RiskRuleRepository rules,
                           WatchlistEntryRepository watchlist, EventPublisher events, Clock clock) {
        this.mapper = mapper;
        this.processed = processed;
        this.transactions = transactions;
        this.signals = signals;
        this.rules = rules;
        this.watchlist = watchlist;
        this.events = events;
        this.clock = clock;
    }

    @KafkaListener(topics = Topics.TXN_SUBMITTED, groupId = GROUP)
    @Transactional
    public void consume(String json) {
        EventEnvelope envelope = events.parse(json);
        if (processed.markProcessed(GROUP, envelope.eventId(), Topics.TXN_SUBMITTED, clock.instant()) == 0) return;

        TransactionSubmitted event = mapper.convertValue(envelope.payload(), TransactionSubmitted.class);
        Transaction tx = transactions.findById(event.transactionId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.transactionId()));
        if (signals.findByTransactionId(tx.getId()).isPresent()) return;

        RiskRule rule = rules.findByCode("WATCHLIST_NAME_MATCH").orElseThrow(
                () -> new IllegalStateException("WATCHLIST_NAME_MATCH rule is missing"));
        BigDecimal threshold = rule.getThreshold();
        WatchlistEntry best = null;
        String matchedName = null;
        BigDecimal bestScore = BigDecimal.ZERO;

        for (String name : new String[]{tx.getPayerName(), tx.getPayeeName()}) {
            for (WatchlistEntry entry : watchlist.findActiveByName(name)) {
                if (best == null || bestScore.compareTo(BigDecimal.ONE) < 0) {
                    best = entry;
                    matchedName = name;
                    bestScore = BigDecimal.ONE;
                }
            }
        }

        boolean hit = best != null && bestScore.compareTo(threshold) >= 0;
        UUID signalId = UUID.randomUUID();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("threshold", threshold);
        details.put("matchedField", best == null ? null :
                best.getFullName().equalsIgnoreCase(tx.getPayerName()) ? "PAYER_NAME" : "PAYEE_NAME");
        details.put("listSource", best == null ? null : best.getListSource());

        ScreeningSignal signal = hit
                ? ScreeningSignal.hit(signalId, tx.getId(), envelope.eventId(), bestScore,
                    best.getId(), matchedName, details)
                : ScreeningSignal.clear(signalId, tx.getId(), envelope.eventId(), bestScore, details);
        signals.save(signal);

        events.enqueue("SCREENING_SIGNAL", tx.getId(), "ScreeningSignalCreated", Topics.TXN_SCREENING_SIGNAL,
                new ScreeningSignalEvent(tx.getId(), signalId, hit, bestScore,
                        hit ? best.getId() : null, hit ? matchedName : null), envelope.correlationId());
    }
}
