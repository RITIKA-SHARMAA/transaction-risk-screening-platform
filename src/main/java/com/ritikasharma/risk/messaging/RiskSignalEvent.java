package com.ritikasharma.risk.messaging;

import com.ritikasharma.risk.domain.TriggeredRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RiskSignalEvent(
        UUID transactionId,
        UUID signalId,
        BigDecimal score,
        List<TriggeredRule> triggeredRules
) {}
