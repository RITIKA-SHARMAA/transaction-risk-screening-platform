package com.ritikasharma.risk.messaging;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionSubmitted(
        UUID transactionId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String payerAccountId,
        String payerName,
        String payerCountry,
        String payeeName,
        String payeeCountry,
        String correlationId
) {}
