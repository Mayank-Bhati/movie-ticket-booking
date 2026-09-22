package com.mayankbhati.movietickets.notification.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OutboxService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void enqueue(String aggregateId, String eventType, Object payload, String dedupeKey) {
        try {
            OffsetDateTime now = OffsetDateTime.now(clock);
            jdbc.update("""
                    INSERT INTO outbox_event(aggregate_id, event_type, payload, status, attempts,
                                             available_at, created_at, dedupe_key)
                    VALUES (:aggregateId, :eventType, :payload, 'PENDING', 0, :now, :now, :dedupeKey)
                    """, Map.of("aggregateId", aggregateId, "eventType", eventType,
                    "payload", objectMapper.writeValueAsString(payload), "now", now,
                    "dedupeKey", dedupeKey));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event", exception);
        }
    }
}

