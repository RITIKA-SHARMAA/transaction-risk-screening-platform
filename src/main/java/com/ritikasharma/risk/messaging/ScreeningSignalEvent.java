package com.ritikasharma.risk.messaging;

import java.math.BigDecimal;
import java.util.UUID;

public record ScreeningSignalEvent(
        UUID transactionId,
        UUID signalId,
        boolean hit,
        BigDecimal matchScore,
        Long matchedEntryId,
        String matchedName
) {}
