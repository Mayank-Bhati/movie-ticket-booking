# ADR 0002: Pessimistic Seat Locking

Status: Accepted

## Decision

Materialize one inventory row per showing/seat and lock requested rows with JPA
`LockModeType.PESSIMISTIC_WRITE` in ascending ID order before changing availability. Hibernate
translates that lock mode to the database's row-locking syntax.

## Rationale

Seat contention is expected, conflicts must be rejected immediately, and overselling is
unacceptable. Pessimistic locking gives a direct database serialization point. Ordered acquisition
reduces deadlock risk, while integration tests verify one winner under concurrent access.

Optimistic locking was rejected because popular shows would create avoidable retries and a more
complex multi-seat rollback path.
