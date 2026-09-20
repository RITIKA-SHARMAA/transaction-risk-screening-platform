package com.ritikasharma.risk.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        String topic,
        UUID aggregateId,
        String correlationId,
        Instant occurredAt,
        JsonNode payload
) {}
