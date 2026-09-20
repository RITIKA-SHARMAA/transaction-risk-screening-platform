package com.ritikasharma.risk.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class SignalAndDecisionRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ScreeningSignalRepository screeningSignalRepository;
    @Autowired
    private RiskSignalRepository riskSignalRepository;
    @Autowired
    private TransactionDecisionRepository decisionRepository;
    @Autowired
    private WatchlistEntryRepository watchlistEntryRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void screeningSignalIsFoundByTransactionId() {
        UUID transactionId = newTransactionId();
        WatchlistEntry entry = watchlistEntryRepository.findActiveByName("Tobias Renquist").getFirst();
        screeningSignalRepository.saveAndFlush(ScreeningSignal.hit(UUID.randomUUID(), transactionId, UUID.randomUUID(),
                new BigDecimal("0.9500"), entry.getId(), entry.getFullName(), Map.of("field", "PAYER_NAME")));
        entityManager.clear();

        ScreeningSignal loaded = screeningSignalRepository.findByTransactionId(transactionId).orElseThrow();
        assertThat(loaded.isHit()).isTrue();
        assertThat(loaded.getMatchedEntryId()).isEqualTo(entry.getId());
        assertThat(loaded.getDetails()).containsEntry("field", "PAYER_NAME");
        assertThat(screeningSignalRepository.findByTransactionId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void secondScreeningSignalForSameTransactionIsRejected() {
        UUID transactionId = newTransactionId();
        screeningSignalRepository.saveAndFlush(clearScreening(transactionId));

        assertThatThrownBy(() -> screeningSignalRepository.saveAndFlush(clearScreening(transactionId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_screening_signals_transaction");
    }

    @Test
    void riskSignalIsFoundByTransactionIdWithTriggeredRules() {
        UUID transactionId = newTransactionId();
        riskSignalRepository.saveAndFlush(new RiskSignal(UUID.randomUUID(), transactionId, UUID.randomUUID(),
                new BigDecimal("45.00"),
                List.of(new TriggeredRule("AMOUNT_HIGH_USD", RuleType.AMOUNT_THRESHOLD, new BigDecimal("20.00")),
                        new TriggeredRule("VELOCITY_1H", RuleType.VELOCITY, new BigDecimal("25.00")))));
        entityManager.clear();

        RiskSignal loaded = riskSignalRepository.findByTransactionId(transactionId).orElseThrow();
        assertThat(loaded.getScore()).isEqualByComparingTo("45");
        assertThat(loaded.getTriggeredRules())
                .extracting(TriggeredRule::code, TriggeredRule::ruleType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AMOUNT_HIGH_USD", RuleType.AMOUNT_THRESHOLD),
                        org.assertj.core.groups.Tuple.tuple("VELOCITY_1H", RuleType.VELOCITY));
        assertThat(riskSignalRepository.findByTransactionId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void decisionIsFoundByTransactionId() {
        UUID transactionId = newTransactionId();
        decisionRepository.saveAndFlush(decision(transactionId, Decision.BLOCK, NOW));
        entityManager.clear();

        TransactionDecision loaded = decisionRepository.findByTransactionId(transactionId).orElseThrow();
        assertThat(loaded.getDecision()).isEqualTo(Decision.BLOCK);
        assertThat(loaded.getReasons()).containsExactly("DECISION_BLOCK");
        assertThat(loaded.getDecidedAt()).isEqualTo(NOW);
        assertThat(decisionRepository.findByTransactionId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void transactionCanOnlyBeDecidedOnce() {
        UUID transactionId = newTransactionId();
        decisionRepository.saveAndFlush(decision(transactionId, Decision.APPROVE, NOW));

        assertThatThrownBy(() -> decisionRepository.saveAndFlush(decision(transactionId, Decision.BLOCK, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_decisions_transaction");
    }

    @Test
    void findByDecisionReturnsOldestFirstUpToLimit() {
        TransactionDecision newest = decision(newTransactionId(), Decision.REVIEW, NOW);
        TransactionDecision oldest = decision(newTransactionId(), Decision.REVIEW, NOW.minus(Duration.ofHours(2)));
        TransactionDecision middle = decision(newTransactionId(), Decision.REVIEW, NOW.minus(Duration.ofHours(1)));
        decisionRepository.saveAll(List.of(newest, oldest, middle,
                decision(newTransactionId(), Decision.APPROVE, NOW.minus(Duration.ofHours(3)))));
        decisionRepository.flush();

        assertThat(decisionRepository.findByDecisionOrderByDecidedAtAsc(Decision.REVIEW, Limit.of(2)))
                .extracting(TransactionDecision::getId)
                .containsExactly(oldest.getId(), middle.getId());
        assertThat(decisionRepository.findByDecisionOrderByDecidedAtAsc(Decision.BLOCK, Limit.of(10))).isEmpty();
    }

    private UUID newTransactionId() {
        return transactionRepository.saveAndFlush(TestData.transaction()).getId();
    }

    private static ScreeningSignal clearScreening(UUID transactionId) {
        return ScreeningSignal.clear(UUID.randomUUID(), transactionId, UUID.randomUUID(), new BigDecimal("0.1200"), Map.of());
    }

    private static TransactionDecision decision(UUID transactionId, Decision decision, Instant decidedAt) {
        return new TransactionDecision(UUID.randomUUID(), transactionId, decision, new BigDecimal("80.00"), false,
                List.of("DECISION_" + decision.name()), decidedAt);
    }
}
