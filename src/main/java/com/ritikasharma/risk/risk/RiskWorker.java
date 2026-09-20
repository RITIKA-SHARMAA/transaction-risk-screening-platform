package com.ritikasharma.risk.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritikasharma.risk.domain.*;
import com.ritikasharma.risk.messaging.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class RiskWorker {
    static final String GROUP = "risk-score-worker";

    private final ObjectMapper mapper;
    private final ProcessedEventRepository processed;
    private final TransactionRepository transactions;
    private final RiskSignalRepository signals;
    private final RiskRuleRepository rules;
    private final CountryRiskRepository countryRisk;
    private final EventPublisher events;
    private final Clock clock;

    public RiskWorker(ObjectMapper mapper, ProcessedEventRepository processed, TransactionRepository transactions,
                      RiskSignalRepository signals, RiskRuleRepository rules, CountryRiskRepository countryRisk,
                      EventPublisher events, Clock clock) {
        this.mapper = mapper;
        this.processed = processed;
        this.transactions = transactions;
        this.signals = signals;
        this.rules = rules;
        this.countryRisk = countryRisk;
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

        List<TriggeredRule> triggered = new ArrayList<>();
        for (RiskRule rule : rules.findBySignalTypeAndEnabledTrueOrderByCodeAsc(SignalType.RISK)) {
            if (fires(rule, tx)) triggered.add(new TriggeredRule(rule.getCode(), rule.getRuleType(), rule.getWeight()));
        }

        BigDecimal score = triggered.stream()
                .map(TriggeredRule::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        UUID signalId = UUID.randomUUID();
        signals.save(new RiskSignal(signalId, tx.getId(), envelope.eventId(), score, triggered));
        events.enqueue("RISK_SIGNAL", tx.getId(), "RiskSignalCreated", Topics.TXN_RISK_SIGNAL,
                new RiskSignalEvent(tx.getId(), signalId, score, triggered), envelope.correlationId());
    }

    private boolean fires(RiskRule rule, Transaction tx) {
        return switch (rule.getRuleType()) {
            case AMOUNT_THRESHOLD -> tx.getCurrency().equalsIgnoreCase(stringParam(rule, "currency"))
                    && tx.getAmount().compareTo(rule.getThreshold()) >= 0;
            case VELOCITY -> {
                int minutes = intParam(rule, "windowMinutes");
                Instant since = clock.instant().minus(minutes, ChronoUnit.MINUTES);
                long count = transactions.countByPayerAccountSince(tx.getPayerAccountId(), since);
                yield count >= rule.getThreshold().longValue();
            }
            case COUNTRY_RISK -> {
                int threshold = rule.getThreshold().intValue();
                int payer = riskScore(tx.getPayerCountry(), rule);
                int payee = riskScore(tx.getPayeeCountry(), rule);
                yield payer >= threshold || payee >= threshold;
            }
            case CROSS_BORDER -> !tx.getPayerCountry().equalsIgnoreCase(tx.getPayeeCountry());
            default -> false;
        };
    }

    private int riskScore(String country, RiskRule rule) {
        return countryRisk.findById(country.toUpperCase(Locale.ROOT))
                .map(CountryRisk::getRiskScore)
                .orElse(intParam(rule, "defaultRiskScore"));
    }

    private String stringParam(RiskRule rule, String key) {
        Object value = rule.getParams().get(key);
        return value == null ? "" : value.toString();
    }

    private int intParam(RiskRule rule, String key) {
        Object value = rule.getParams().get(key);
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
}
