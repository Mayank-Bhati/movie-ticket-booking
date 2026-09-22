# Video Walkthrough Outline

Target length: 8-10 minutes.

## 1. Problem and Scope (1 minute)

- State the seat-allocation invariant and the admin/customer roles.
- Mention deliberate exclusions: frontend, deployment, distributed services, advanced auth.

## 2. Architecture (2 minutes)

- Open `docs/architecture/system-design.md`.
- Explain the feature-oriented modular monolith and why SQL-first persistence was chosen.
- Walk through the hold/booking sequence and transactional outbox.

## 3. Code Tour (2 minutes)

- Show the feature packages and Flyway schema.
- Point out ordered `FOR UPDATE` seat locking, idempotency uniqueness, pricing snapshots, and the
  payment/notification ports.

## 4. Live API Flow (2 minutes)

- Start the demo profile.
- Use `api.http` to register, browse, hold, book with `WELCOME10`, list history, and cancel.
- Show a `409` for an unavailable seat or a `410` for an expired hold.

## 5. Testing and AI Workflow (1-2 minutes)

- Run `./mvnw test` and open the JaCoCo report.
- Highlight the two-thread contention test, rollback test, refund test, and HTTP RBAC test.
- Open `docs/ai-workflow.md`, `AGENTS.md`, and the raw requirement files.

