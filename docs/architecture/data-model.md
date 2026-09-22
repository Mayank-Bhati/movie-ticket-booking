# Data Model

```mermaid
erDiagram
    CITY ||--o{ THEATER : contains
    THEATER ||--o{ SCREEN : contains
    SCREEN ||--o{ SEAT : defines
    MOVIE ||--o{ SHOWING : scheduled_as
    SCREEN ||--o{ SHOWING : hosts
    REFUND_POLICY ||--o{ REFUND_RULE : contains
    REFUND_POLICY ||--o{ SHOWING : governs
    SHOWING ||--o{ SHOW_SEAT : materializes
    SEAT ||--o{ SHOW_SEAT : snapshots
    APP_USER ||--o{ SEAT_HOLD : creates
    SEAT_HOLD ||--o{ SEAT_HOLD_ITEM : contains
    SHOW_SEAT ||--o{ SEAT_HOLD_ITEM : references
    APP_USER ||--o{ BOOKING : owns
    BOOKING ||--|{ BOOKING_ITEM : contains
    SHOW_SEAT ||--o{ BOOKING_ITEM : references
    BOOKING ||--|| PAYMENT : captures
    BOOKING ||--o| REFUND : may_create
```

## Inventory State

`show_seat` is intentionally materialized when an admin creates a showing. It snapshots seat
category and price, and carries the mutable allocation state.

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE
    AVAILABLE --> HELD: create hold
    HELD --> AVAILABLE: hold expires
    HELD --> BOOKED: payment captured
    BOOKED --> AVAILABLE: booking cancelled
```

The database unique constraint on `(showing_id, seat_id)` guarantees exactly one inventory row for a
physical seat at a showing. Application transactions lock these rows before state transitions.

## Money and Time

- Monetary columns use integer minor units to avoid floating-point rounding.
- Pricing multipliers use basis points: `10000 = 1.00x`, `15000 = 1.50x`.
- Timestamps use `TIMESTAMP WITH TIME ZONE` and application calculations use an injected UTC clock.
- Theater-local weekend evaluation uses the city's IANA timezone.

