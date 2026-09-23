# System Design

## Context

```mermaid
C4Context
    title Movie Ticket Booking - System Context
    Person(customer, "Customer", "Browses shows, holds and books seats, cancels bookings")
    Person(admin, "Admin", "Maintains venues, layouts, pricing and policies")
    System(system, "Movie Ticket Booking API", "Serializes seat allocation and manages booking lifecycle")
    System_Ext(payment, "Payment Provider", "Capture and refund; represented by a deterministic adapter")
    System_Ext(channel, "Notification Channel", "Email/SMS; represented by a logging adapter")
    Rel(customer, system, "HTTPS/JSON")
    Rel(admin, system, "HTTPS/JSON")
    Rel(system, payment, "Port")
    Rel(system, channel, "Outbox dispatcher")
```

## Modules

```mermaid
flowchart TB
    subgraph Runtime[Spring Boot modular monolith]
      IA[Identity API] --> IS[Identity service]
      CA[Catalog API] --> CS[Catalog service]
      BA[Booking API] --> BS[Booking service]
      BS --> PG[Payment gateway port]
      BS --> OB[Outbox service]
      RS[Reminder scheduler] --> OB
      OD[Outbox dispatcher] --> NS[Notification sender port]
      IS --> JPA[Spring Data repositories]
      CS --> JPA
      BS --> JPA
      OB --> JPA
      OD --> JPA
    end
    JPA --> H[Hibernate]
    H --> DB[(Relational database)]
```

This is a single deployable process and one relational database. Feature packaging preserves clear
ownership without introducing distributed transactions or operational overhead that the assignment
does not require.

## Hold Sequence

```mermaid
sequenceDiagram
    participant C as Customer
    participant B as Booking service
    participant DB as Database
    C->>B: POST /holds (showing, seat IDs)
    B->>DB: Release expired holds
    B->>DB: JPA PESSIMISTIC_WRITE, ordered by inventory ID
    alt every seat is AVAILABLE
      B->>DB: Insert hold + items
      B->>DB: Mark seats HELD with expiry
      DB-->>B: Commit
      B-->>C: 201 hold + expiresAt
    else any seat unavailable
      DB-->>B: Rollback
      B-->>C: 409 SEAT_UNAVAILABLE
    end
```

Ordering locks by inventory ID gives concurrent transactions a consistent acquisition order. The
first contender changes the state while holding the row lock; a later contender sees `HELD` or
`BOOKED` after it acquires the lock and receives `409`.

## Booking Sequence

```mermaid
sequenceDiagram
    participant C as Customer
    participant B as Booking service
    participant DB as Database
    participant P as Payment port
    participant D as Outbox dispatcher
    C->>B: POST /bookings + Idempotency-Key
    B->>DB: Return prior booking if key exists
    B->>DB: Lock hold, seats, and optional discount
    B->>P: Capture payment with stable idempotency key
    P-->>B: Provider reference
    B->>DB: Insert booking, items, payment
    B->>DB: Mark seats BOOKED and hold CONVERTED
    B->>DB: Insert BOOKING_CONFIRMED outbox event
    DB-->>B: Commit once
    B-->>C: 201 booking
    D->>DB: Read pending event
    D->>D: Deliver without blocking request
    D->>DB: Mark event SENT
```

## Consistency and Failure Behavior

| Failure | Behavior |
|---|---|
| Two users hold one seat | Row lock serializes attempts; one succeeds |
| Hold expires | Lazy/scheduled cleanup returns inventory to `AVAILABLE` |
| Payment is declined | Transaction rolls back; the hold remains retryable |
| Client retries confirmation | Unique idempotency key returns the original booking |
| Notification sender fails | Booking remains confirmed; outbox retries independently |
| Cancellation succeeds | Booking, refund decision, released seats, and event commit together |

## Scaling Path

The current design scales vertically and through multiple stateless API instances sharing
PostgreSQL; row locks remain the serialization point. The next practical steps would be pagination,
read caching for catalog queries, partitioning old showing inventory, and a dedicated outbox worker.
External payment would require a pending-payment state plus webhook reconciliation. These are
documented paths, not partially implemented distributed machinery.
