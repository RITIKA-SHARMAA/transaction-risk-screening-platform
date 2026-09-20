package com.ritikasharma.risk.api;

import com.ritikasharma.risk.domain.Decision;
import com.ritikasharma.risk.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReviewItemResponse(
        UUID transactionId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String payerName,
        String payeeName,
        String payerCountry,
        String payeeCountry,
        TransactionStatus status,
        Decision decision,
        BigDecimal riskScore,
        boolean screeningHit,
        Instant decidedAt
) {}
