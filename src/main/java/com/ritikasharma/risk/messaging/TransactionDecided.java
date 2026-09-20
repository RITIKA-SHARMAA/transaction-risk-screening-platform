package com.ritikasharma.risk.messaging;

import com.ritikasharma.risk.domain.Decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionDecided(
        UUID transactionId,
        Decision decision,
        BigDecimal riskScore,
        boolean screeningHit,
        List<String> reasons,
        Instant decidedAt
) {}
