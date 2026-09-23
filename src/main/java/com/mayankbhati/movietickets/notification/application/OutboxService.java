package com.mayankbhati.movietickets.notification.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayankbhati.movietickets.notification.domain.OutboxEvent;
import com.mayankbhati.movietickets.notification.infrastructure.OutboxEventRepository;

@Service
public class OutboxService {
    private final OutboxEventRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String aggregateId, String eventType, Object payload, String dedupeKey) {
        try {
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (!events.existsByDedupeKey(dedupeKey)) {
                events.save(new OutboxEvent(aggregateId, eventType,
                        objectMapper.writeValueAsString(payload), now, dedupeKey));
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event", exception);
        }
    }
}
