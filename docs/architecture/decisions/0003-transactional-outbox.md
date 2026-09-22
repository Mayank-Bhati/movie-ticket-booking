# ADR 0003: Transactional Outbox

Status: Accepted

## Decision

Write notification intent to `outbox_event` in the same transaction as booking or cancellation.
A scheduled dispatcher sends events later through a notification port.

## Rationale

Sending email/SMS inside the request would increase latency and allow channel failure to roll back a
valid booking. Publishing after commit without an outbox could lose messages during a process crash.
The outbox provides atomic intent and non-blocking delivery without introducing a message broker.

