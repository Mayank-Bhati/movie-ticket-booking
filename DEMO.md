# Demo walkthrough (Postman)

A step-by-step demo of the whole system, suitable for a screen recording. Takes about 3–4 minutes.

## Setup (once)

1. Start the app — no database setup needed:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
   ```
   Wait for `Started MovieTicketBookingApplication`.
2. In Postman: **Import → File → `postman_collection.json`** (in the project root).
3. That's it. The collection captures tokens and ids automatically — just send the requests
   **top to bottom**. For a clean re-run later, restart the app (the H2 database resets).

> Sample data is pre-seeded: Bangalore → PVR Forum Mall → Screen 1 (22 seats: rows A/B regular,
> row C premium) → the movie *Inception* with two shows. Show 1 has seat ids 1–22.
> Admin account: `admin@moviebooking.com` / `admin123`.

## The demo, step by step

### Folder 1 — Login & Setup

| # | Request | What to expect / say |
|---|---------|----------------------|
| 1.1 | **Admin login** | `200` with a JWT. Token is saved automatically for admin requests. |
| 1.2 | **Register customer** | `201` with a JWT — customers self-register. (Re-running on the same app run returns `409 already registered` — then just use 1.3.) |
| 1.3 | **Customer login** | Only needed if 1.2 returned 409. |

### Folder 2 — Browse (public, no token)

| # | Request | What to expect / say |
|---|---------|----------------------|
| 2.1 | **List cities** | Bangalore, seeded by the demo profile. |
| 2.2 | **List movies** | Inception. |
| 2.3 | **Shows for movie 1** | Two shows with base prices 200 and 250. |
| 2.4 | **Seat map for show 1** | All 22 seats `AVAILABLE`. Point out premium seats (row C) cost more — pricing tiers at work. Weekend shows also carry a 1.25× multiplier. |

### Folder 3 — Hold → Book → Cancel (the core flow)

| # | Request | What to expect / say |
|---|---------|----------------------|
| 3.1 | **Hold seats 1 and 2** | `201` with a hold id, an expiry ~5 minutes out, and the subtotal. These seats are now `HELD` — locked in the database, so a concurrent request for the same seats gets `409`. |
| 3.2 | **Confirm booking** | `201`: status `CONFIRMED`, payment `SUCCESS`, booking reference. Seats flip to `BOOKED`. |
| 3.3 | **Booking history** | The confirmed booking with its seats and totals. |
| 3.4 | **My notifications** | A `BOOKING_CONFIRMED` entry — written in the same transaction as the booking (transactional outbox), delivered by a background dispatcher, `SENT` within ~5s. |
| 3.5 | **Cancel the booking** | `200` with the refund: show is >24h away → `refundPercent: 100`, full refund per the configurable policy. |
| 3.6 | **Seat map again** | Seats 1 and 2 are `AVAILABLE` again. |

### Folder 4 — Discounts & Admin

| # | Request | What to expect / say |
|---|---------|----------------------|
| 4.1 | **Create SAVE10** (admin) | 10% off, min order 100, capped at 100 uses. |
| 4.2 | **Preview** | Subtotal 1000 → discount 100 → total 900, without booking anything. |
| 4.3 | **Hold seats 3 and 4** | New hold for the discounted booking. |
| 4.4 | **Book with SAVE10** | The response shows subtotal, `discountAmount` (10%), and the reduced total. Usage count increments — under a lock, so a capped code can't be over-redeemed concurrently. |
| 4.5 | **List pricing tiers** (admin) | The three seeded multipliers. |
| 4.6 | **Update premium multiplier** | Runtime pricing change — new shows price with 1.75×. |
| 4.7 | **Refund policy tiers** (admin) | ≥24h → 100%, ≥2h → 50%, else 0% — all editable. |
| 4.8 | **RBAC check** | Admin endpoint with the *customer* token → `403 Forbidden`. Role-based access control enforced. |

## Handy extras to show if asked

- Wrong method: `GET /api/auth/login` in a browser → proper `405`.
- Validation: register with a bad email → `400` with per-field errors.
- Double-booking protection: send 3.1 twice quickly → the second returns `409 Seat is currently held`.
