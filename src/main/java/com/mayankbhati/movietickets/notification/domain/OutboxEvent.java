package com.mayankbhati.movietickets.notification.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "available_at", nullable = false)
    private OffsetDateTime availableAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
    @Column(name = "dedupe_key", nullable = false, unique = true, length = 160)
    private String dedupeKey;

    protected OutboxEvent() { }
    public OutboxEvent(String aggregateId, String eventType, String payload,
                       OffsetDateTime availableAt, String dedupeKey) {
        this.aggregateId = aggregateId; this.eventType = eventType; this.payload = payload;
        this.status = "PENDING"; this.availableAt = availableAt;
        this.createdAt = availableAt; this.dedupeKey = dedupeKey;
    }
    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public void markSent(OffsetDateTime now) { status = "SENT"; sentAt = now; attempts++; }
    public void markFailedAttempt(OffsetDateTime retryAt) {
        attempts++; status = attempts >= 5 ? "FAILED" : "PENDING"; availableAt = retryAt;
    }
}
