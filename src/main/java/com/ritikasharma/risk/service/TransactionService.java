package com.ritikasharma.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritikasharma.risk.api.TransactionRequest;
import com.ritikasharma.risk.api.TransactionResponse;
import com.ritikasharma.risk.common.IdempotencyConflictException;
import com.ritikasharma.risk.common.ResourceNotFoundException;
import com.ritikasharma.risk.domain.*;
import com.ritikasharma.risk.messaging.EventPublisher;
import com.ritikasharma.risk.messaging.Topics;
import com.ritikasharma.risk.messaging.TransactionSubmitted;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final IdempotencyRecordRepository idempotency;
    private final TransactionDecisionRepository decisions;
    private final EventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TransactionService(TransactionRepository transactions, IdempotencyRecordRepository idempotency,
                              TransactionDecisionRepository decisions, EventPublisher events,
                              ObjectMapper objectMapper, Clock clock) {
        this.transactions = transactions;
        this.idempotency = idempotency;
        this.decisions = decisions;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public TransactionResponse submit(String key, TransactionRequest request, String merchantId) {
        if (key == null || key.isBlank() || key.length() > 255)
            throw new IdempotencyConflictException("Idempotency-Key must be non-empty and at most 255 characters.");

        String hash = hash(request);
        Instant now = clock.instant();
        String correlationId = org.slf4j.MDC.get("correlationId");
        int inserted = idempotency.insertIfAbsent(UUID.randomUUID(), key, merchantId, hash,
                now.plus(Duration.ofDays(7)), now);
        if (inserted == 0) {
            IdempotencyRecord record = idempotency.findByIdempotencyKeyAndMerchantId(key, merchantId)
                    .orElseThrow(() -> new IdempotencyConflictException("Idempotency record is unavailable."));
            if (!record.matchesRequest(hash))
                throw new IdempotencyConflictException("Idempotency-Key was already used with a different request.");
            if (record.getStatus() == IdempotencyStatus.COMPLETED)
                return responseFor(record.getTransactionId(), merchantId);
            throw new IdempotencyConflictException("A request with this Idempotency-Key is already in progress.");
        }

        UUID transactionId = UUID.randomUUID();
        Transaction tx = new Transaction(transactionId, merchantId, request.amount(),
                request.currency().toUpperCase(), request.payerAccountId(), request.payerName(),
                request.payerCountry().toUpperCase(), request.payeeName(), request.payeeCountry().toUpperCase(),
                correlationId);
        transactions.save(tx);

        IdempotencyRecord record = idempotency.findByIdempotencyKeyAndMerchantId(key, merchantId).orElseThrow();
        transactions.flush();

        events.enqueue("TRANSACTION", transactionId, "TransactionSubmitted", Topics.TXN_SUBMITTED,
                new TransactionSubmitted(transactionId, merchantId, tx.getAmount(), tx.getCurrency(),
                        tx.getPayerAccountId(), tx.getPayerName(), tx.getPayerCountry(),
                        tx.getPayeeName(), tx.getPayeeCountry(), correlationId), correlationId);

        TransactionResponse response = toResponse(tx);
        try {
            record.complete(202, objectMapper.writeValueAsString(response), transactionId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id, String merchantId) {
        return responseFor(id, merchantId);
    }

    private TransactionResponse responseFor(UUID id, String merchantId) {
        Transaction tx = transactions.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
        return toResponse(tx);
    }

    private TransactionResponse toResponse(Transaction tx) {
        var decision = decisions.findByTransactionId(tx.getId()).orElse(null);
        return new TransactionResponse(tx.getId(), tx.getMerchantId(), tx.getAmount(), tx.getCurrency(),
                tx.getPayerAccountId(), tx.getPayerName(), tx.getPayerCountry(), tx.getPayeeName(),
                tx.getPayeeCountry(), tx.getStatus(), decision == null ? null : decision.getDecision(),
                decision == null ? null : decision.getRiskScore(),
                decision != null && decision.isScreeningHit(), tx.getCreatedAt(), tx.getCorrelationId());
    }

    private String hash(TransactionRequest request) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash request", e);
        }
    }
}
