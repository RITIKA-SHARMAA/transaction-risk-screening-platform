package com.ritikasharma.risk.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class IdempotencyRecordRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));

    @Autowired
    private IdempotencyRecordRepository repository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void duplicateKeyForSameMerchantIsRejected() {
        repository.saveAndFlush(TestData.idempotencyRecord("key-1", "merchant-a", TOMORROW));

        assertThatThrownBy(() -> repository.saveAndFlush(TestData.idempotencyRecord("key-1", "merchant-a", TOMORROW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_idempotency_records_key_merchant");
    }

    @Test
    void sameKeyIsAllowedForDifferentMerchants() {
        repository.saveAndFlush(TestData.idempotencyRecord("key-1", "merchant-a", TOMORROW));
        repository.saveAndFlush(TestData.idempotencyRecord("key-1", "merchant-b", TOMORROW));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void findByKeyAndMerchantReturnsOnlyThatMerchantsRecord() {
        IdempotencyRecord a = repository.saveAndFlush(TestData.idempotencyRecord("key-1", "merchant-a", TOMORROW));
        repository.saveAndFlush(TestData.idempotencyRecord("key-1", "merchant-b", TOMORROW));

        assertThat(repository.findByIdempotencyKeyAndMerchantId("key-1", "merchant-a"))
                .get().extracting(IdempotencyRecord::getId).isEqualTo(a.getId());
        assertThat(repository.findByIdempotencyKeyAndMerchantId("key-2", "merchant-a")).isEmpty();
        assertThat(repository.findByIdempotencyKeyAndMerchantId("key-1", "merchant-c")).isEmpty();
    }

    @Test
    void completedRecordStoresResponseForReplay() {
        Transaction transaction = transactionRepository.saveAndFlush(TestData.transaction());
        IdempotencyRecord record = repository.saveAndFlush(TestData.idempotencyRecord("key-1", "merchant-a", TOMORROW));

        record.complete(202, "{\"transactionId\":\"" + transaction.getId() + "\",\"status\":\"PENDING\"}",
                transaction.getId());
        repository.saveAndFlush(record);
        entityManager.clear();

        IdempotencyRecord loaded = repository.findByIdempotencyKeyAndMerchantId("key-1", "merchant-a").orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(loaded.getResponseStatus()).isEqualTo(202);
        assertThat(loaded.getResponseBody()).contains("\"status\": \"PENDING\"");
        assertThat(loaded.getTransactionId()).isEqualTo(transaction.getId());
        assertThat(loaded.matchesRequest("a".repeat(64))).isTrue();
        assertThat(loaded.matchesRequest("b".repeat(64))).isFalse();
    }

    @Test
    void deleteExpiredRemovesOnlyExpiredRecords() {
        repository.save(TestData.idempotencyRecord("expired", "merchant-a", NOW.minusSeconds(1)));
        repository.save(TestData.idempotencyRecord("live", "merchant-a", NOW.plusSeconds(1)));

        assertThat(repository.deleteExpired(NOW)).isEqualTo(1);

        assertThat(repository.findAll()).extracting(IdempotencyRecord::getIdempotencyKey).containsExactly("live");
    }
}
