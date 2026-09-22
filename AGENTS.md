# Engineering Guidelines

## Mission

Build a correctness-first movie ticket booking API. Seat allocation, payment idempotency, and
refund calculations are the highest-risk paths and must be covered by integration tests.

## Architecture rules

- Organize code by business capability: `identity`, `catalog`, `booking`, and `notification`.
- Keep HTTP concerns in `api` packages, use cases in `application`, domain rules in `domain`, and
  SQL/external integrations in `infrastructure`.
- A feature may depend on another feature only through its public application interface or a
  narrow port. Do not share database repository implementations between features.
- Use explicit SQL through Spring JDBC. Schema changes belong in versioned Flyway migrations.
- Store money as integer minor units (`long`, cents/paise), never floating point.
- Use an injected `Clock` for time-dependent behavior.

## Working agreement

- Run `./mvnw test` before committing.
- Add an integration test for every concurrency or transaction boundary change.
- Update the OpenAPI examples and architecture decisions when a contract or major design choice
  changes.
- Never commit credentials, generated build output, local database files, or access tokens.
