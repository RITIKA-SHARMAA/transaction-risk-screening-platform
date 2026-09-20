package com.ritikasharma.risk.api;

import com.ritikasharma.risk.domain.Decision;
import com.ritikasharma.risk.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String merchantId,
        BigDecimal amount,
        String currency,
        String payerAccountId,
        String payerName,
        String payerCountry,
        String payeeName,
        String payeeCountry,
        TransactionStatus status,
        Decision decision,
        BigDecimal riskScore,
        boolean screeningHit,
        Instant createdAt,
        String correlationId
) {}
