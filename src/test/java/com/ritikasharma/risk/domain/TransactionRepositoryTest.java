package com.ritikasharma.risk.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsAsPendingAndRoundTrips() {
        Transaction saved = repository.saveAndFlush(TestData.transaction());
        entityManager.clear();

        Transaction loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(loaded.getAmount()).isEqualByComparingTo("125.50");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void applyingDecisionUpdatesStatusAndVersion() {
        Transaction saved = repository.saveAndFlush(TestData.transaction());
        entityManager.clear();

        Transaction loaded = repository.findById(saved.getId()).orElseThrow();
        loaded.applyDecision(Decision.REVIEW);
        repository.saveAndFlush(loaded);
        entityManager.clear();

        Transaction reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.IN_REVIEW);
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThatThrownBy(() -> reloaded.applyDecision(Decision.APPROVE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findByIdAndMerchantIdIsScopedToMerchant() {
        Transaction saved = repository.saveAndFlush(TestData.transaction("merchant-a", "payer-1"));

        assertThat(repository.findByIdAndMerchantId(saved.getId(), "merchant-a")).isPresent();
        assertThat(repository.findByIdAndMerchantId(saved.getId(), "merchant-b")).isEmpty();
        assertThat(repository.findByIdAndMerchantId(UUID.randomUUID(), "merchant-a")).isEmpty();
    }

    @Test
    void countByPayerAccountSinceCountsOnlyThatPayerInsideTheWindow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Transaction recent1 = repository.save(TestData.transaction(TestData.MERCHANT, "payer-v"));
        Transaction recent2 = repository.save(TestData.transaction(TestData.MERCHANT, "payer-v"));
        Transaction old = repository.save(TestData.transaction(TestData.MERCHANT, "payer-v"));
        repository.save(TestData.transaction(TestData.MERCHANT, "payer-other"));
        repository.flush();
        backdate(recent1.getId(), now.minus(Duration.ofMinutes(5)));
        backdate(recent2.getId(), now.minus(Duration.ofMinutes(59)));
        backdate(old.getId(), now.minus(Duration.ofMinutes(61)));

        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        assertThat(repository.countByPayerAccountSince("payer-v", oneHourAgo)).isEqualTo(2);
        assertThat(repository.countByPayerAccountSince("payer-v", now.minus(Duration.ofDays(1)))).isEqualTo(3);
        assertThat(repository.countByPayerAccountSince("payer-unknown", oneHourAgo)).isZero();
    }

    @Test
    void nonPositiveAmountIsRejectedByTheDatabase() {
        Transaction invalid = new Transaction(UUID.randomUUID(), TestData.MERCHANT, BigDecimal.ZERO, "USD",
                "payer", "Jane", "US", "Acme", "US", null);

        assertThatThrownBy(() -> repository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_amount_positive");
    }

    private void backdate(UUID transactionId, Instant createdAt) {
        jdbc.update("UPDATE transactions SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), transactionId);
    }
}
