# ADR 0004: Hibernate Persistence With Flyway-Owned Schema

Status: Accepted

## Decision

Use Jakarta Persistence with Hibernate for entity mapping, dirty checking, associations, batching,
and transactional persistence. Use Spring Data `JpaRepository` interfaces and derived query methods
for persistence. Fetch plans and consistency locks are declared with `@EntityGraph` and `@Lock`.
Flyway remains the only schema migration mechanism; Hibernate runs with `ddl-auto=validate` and Open
Session in View is disabled.

## Rationale

The domain contains durable aggregates and relationships that benefit from explicit entity models.
Application services should describe booking and catalog use cases without containing SQL or row
mapping code. Lazy associations and focused entity graphs make loading decisions explicit, while
JPA lock modes preserve the database-backed concurrency model. Spring Data repositories provide
the required CRUD and derived-query behavior without a hand-written persistence layer.

Hibernate schema generation was rejected because production schema changes must be versioned and
reviewable. Open Session in View was rejected because it hides database access in HTTP serialization
and can produce unbounded query behavior.
