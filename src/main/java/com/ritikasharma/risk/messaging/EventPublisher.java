package com.ritikasharma.risk.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritikasharma.risk.domain.OutboxEvent;
import com.ritikasharma.risk.domain.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class EventPublisher {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outbox;
    private final Clock clock;

    public EventPublisher(ObjectMapper objectMapper, OutboxEventRepository outbox, Clock clock) {
        this.objectMapper = objectMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    public UUID enqueue(String aggregateType, UUID aggregateId, String eventType, String topic,
                        Object payload, String correlationId) {
        try {
            UUID eventId = UUID.randomUUID();
            EventEnvelope envelope = new EventEnvelope(
                    eventId, eventType, topic, aggregateId, correlationId, clock.instant(),
                    objectMapper.valueToTree(payload));
            outbox.save(new OutboxEvent(eventId, aggregateType, aggregateId, eventType, topic,
                    objectMapper.writeValueAsString(envelope), correlationId, clock.instant()));
            return eventId;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize event payload", e);
        }
    }

    public EventEnvelope parse(String json) {
        try {
            return objectMapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event envelope", e);
        }
    }
}
