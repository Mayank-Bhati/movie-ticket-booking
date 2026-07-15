# Movie Ticket Booking System

A backend for a movie ticket booking platform operating at scale — multiple cities, theaters,
screens, and shows, with seat-level booking, time-bound seat holds, tiered pricing, discount codes,
payment, refunds under a configurable policy, and non-blocking notifications. The system serializes
concurrent bookings so a seat can never be double-allocated.

Built with **Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, Flyway, MySQL**
(with an in-memory **H2** demo profile and test suite).

---

## Quick start

### Option A — Zero-setup demo (H2, no database required)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Runs against in-memory H2. Flyway creates all tables, reference data and a sample catalog are
seeded, and the API is available at `http://localhost:8080`. Nothing to install or configure.

Once running you can immediately:

```bash
curl http://localhost:8080/api/movies
curl http://localhost:8080/api/shows/1/seats
```

### Option B — Real MySQL (default profile)

Requires a running MySQL instance. The database and all tables are created automatically on first
start (the JDBC URL uses `createDatabaseIfNotExist=true` and Flyway applies the schema).

```bash
DB_USERNAME=root DB_PASSWORD=your_password ./mvnw spring-boot:run
```

Credentials are read from `DB_USERNAME` / `DB_PASSWORD` (defaults: `root` / empty).

### Seeded admin account

On first start an admin user is created (configurable via `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`):

```
email:    admin@moviebooking.com
password: admin123
```

Customers self-register via `POST /api/auth/register`.

### Run the tests

```bash
./mvnw test
```

46 tests (unit + integration) run against H2. A JaCoCo coverage report is written to
`target/site/jacoco/index.html` (≈93% line coverage).

---

## Roles

| Role | Capabilities |
|------|--------------|
| **ADMIN** | Manage cities, theaters, screens & seat layouts, movies, shows, pricing tiers, discount codes, refund policies |
| **CUSTOMER** | Browse shows, hold & book seats, view booking history, cancel bookings |

Authentication is stateless JWT. Send the token as `Authorization: Bearer <token>`.

---

## API reference

### Authentication (public)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register a customer, returns a JWT |
| POST | `/api/auth/login` | Log in, returns a JWT |

### Browse (public)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/cities` | List cities |
| GET | `/api/movies` | List movies |
| GET | `/api/movies/{id}` | Movie details |
| GET | `/api/movies/{id}/shows?cityId=` | Shows for a movie, optionally filtered by city |
| GET | `/api/shows/{id}` | Show details |
| GET | `/api/shows/{id}/seats` | Live seat map (status + price per seat) |

### Booking (CUSTOMER)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/holds` | Hold seats for a show (time-bound) |
| POST | `/api/bookings` | Confirm a booking from a hold (applies discount, charges payment) |
| GET | `/api/bookings` | Booking history |
| GET | `/api/bookings/{id}` | Booking details |
| DELETE | `/api/bookings/{id}` | Cancel a booking (refund per policy) |
| GET | `/api/notifications` | Current user's notifications |
| POST | `/api/discounts/preview` | Preview a discount code against a subtotal |

### Admin (ADMIN)
| Method | Path | Description |
|--------|------|-------------|
| POST / DELETE | `/api/admin/cities`, `/api/admin/cities/{id}` | Manage cities |
| POST / GET | `/api/admin/theaters` | Manage / list theaters |
| POST / GET | `/api/admin/screens`, `/api/admin/screens/{id}` | Manage screens & seat layouts |
| POST / PUT / DELETE / GET | `/api/admin/movies`, `/api/admin/movies/{id}` | Manage movies |
| POST | `/api/admin/shows` | Create a show (materializes its seats) |
| GET / PUT / DELETE | `/api/admin/pricing-tiers` | Manage pricing multipliers |
| GET / POST / PUT / DELETE | `/api/admin/discount-codes` | Manage discount codes |
| GET / PUT / DELETE | `/api/admin/refund-policies` | Manage refund policy tiers |

Two runnable request collections are included:
- [`postman_collection.json`](postman_collection.json) — import into Postman and send the requests
  top to bottom (tokens and ids are captured automatically). [`DEMO.md`](DEMO.md) is the matching
  step-by-step walkthrough.
- [`api.http`](api.http) — the same flow for IntelliJ's built-in HTTP client.

---

## How it works

### Domain model

`City → Theater → Screen → Seat` defines the venue and its seat layout. A `Movie` plays as a `Show`
on a screen at a time. Creating a show **materializes one `ShowSeat` per seat** — these rows carry
per-show status (`AVAILABLE` / `HELD` / `BOOKED`) and a price snapshot, and are the unit of
concurrency control. A `Booking` groups `BookingSeat`s and links a `Payment` and, on cancellation, a
`Refund`.

### Concurrency — no double-allocation

Holding or booking a seat runs in a single transaction that locks the target `ShowSeat` rows with
`SELECT … FOR UPDATE` (`PESSIMISTIC_WRITE`), ordered by id to avoid deadlocks. Concurrent attempts on
the same seat are serialized: the first transaction wins, later ones observe the seat as `HELD` or
`BOOKED` and are rejected with `409`. A `UNIQUE (show_id, seat_id)` constraint backs this at the
database level. Verified by an integration test firing 20 threads at one seat — exactly one wins.

### Time-bound holds

A hold reserves seats for a configurable TTL (default 5 minutes). Holds are released two ways: lazily,
when another user tries to grab a seat whose hold has lapsed, and proactively by a scheduled sweeper
that frees expired holds. Booking must happen before the hold expires, otherwise it returns `410`.

### Pricing

Seat price = `base price × seat-type multiplier × weekend multiplier` (weekend applies to Saturday and
Sunday shows). Multipliers live in the `pricing_tiers` table and are admin-configurable at runtime.

### Discounts

`PERCENT` or `FLAT` codes, each with an optional minimum order amount, usage cap, and validity window.
A discount is never larger than the order total. When a booking redeems a code the code row is locked
`FOR UPDATE` so its usage count cannot exceed the cap under concurrent redemptions.

### Payment

Payment is abstracted behind a `PaymentGateway` interface with a mock implementation that approves any
positive charge and returns a transaction reference. A real provider (Stripe/Razorpay) would replace
the bean without touching the booking flow. Charge and confirmation run in the booking transaction —
on failure everything rolls back, leaving the hold intact so the customer can retry.

### Refunds

Cancelling a confirmed booking releases its seats and issues a refund from a configurable policy keyed
by "minimum hours before show". Default tiers: ≥24h → 100%, ≥2h → 50%, otherwise 0%. Admins can change
the tiers at runtime.

### Notifications — non-blocking

Booking confirmations and cancellations are written as `PENDING` rows in the **same transaction** as
the booking change (transactional outbox), so they are atomic with it and never block the response. A
background dispatcher delivers them and marks them `SENT`; a scheduled job enqueues show reminders. The
delivery channel is behind a `NotificationSender` interface (logging stub today, email/SMS in
production).

---

## Assumptions & scoping decisions

- **Customer self-registration** creates `CUSTOMER` accounts only; admins are provisioned by seeding
  (an admin-managed user API was left out of scope).
- **Payment is mocked** deterministically; there is no external gateway integration.
- **Payment runs inside the booking transaction.** This is correct for the in-process mock. A real
  gateway call is slow and can time out, so production systems keep the booking `PENDING`, call the
  gateway outside the seat lock, and confirm asynchronously via webhook with idempotency keys — this
  is called out as future work (distributed flows were out of scope).
- **Notifications are logged**, not actually emailed/sent.
- **Discount usage is not restored on cancellation** — a redeemed code counts as used.
- **List endpoints are unpaginated** and **`ShowSeat`/`Seat` rows are inserted per-row** (IDENTITY id
  generation precludes JDBC batch inserts). Both are acceptable at assignment scale and noted as
  "at scale" future work.
- **H2 in MySQL-compatibility mode** backs tests and the demo profile; production uses MySQL. The
  Flyway migrations are written to run on both.

---

## Tech stack & rationale

| Choice | Why |
|--------|-----|
| **Spring Boot** | Batteries-included web, security, data, validation, scheduling |
| **MySQL + Flyway** | Real relational persistence with versioned, reviewable schema and DB-level constraints |
| **Pessimistic locking** | The clearest, most correct way to serialize seat contention |
| **JWT / Spring Security** | Stateless RBAC without the OAuth/SSO complexity (out of scope) |
| **H2 (tests + demo)** | Fast, isolated tests and a zero-setup way to run the app |
| **Transactional outbox** | Atomic, non-blocking notifications without a message broker |

## Project layout

```
src/main/java/com/moviebooking/
  auth/          registration, login, JWT issuance
  booking/       holds, booking, cancellation, payment, hold-expiry sweeper
  catalog/       cities, theaters, screens, movies, shows, browse
  pricing/       pricing tiers, discount codes
  refund/        refund policy + refund calculation
  notification/  transactional outbox + dispatcher + reminders
  security/      Spring Security config, JWT filter/service
  web/           global error handling
  entity/ repository/ config/
src/main/resources/db/migration/   Flyway migrations
src/test/java/...                   unit + integration tests
```
