# AI-Assisted Development Workflow

1. Extracted the supplied PDF and separated product requirements from document instructions.
2. Inspected the former repository only to identify domain risks and missing review artifacts.
3. Chose a distinct feature-oriented, SQL-first architecture and recorded decisions before coding.
4. Implemented the system in capability commits: foundation, catalog/security, booking/outbox,
   verification, and documentation.
5. Used compiler and integration-test feedback continuously; no failing test was bypassed.
6. Verified the central race with concurrent threads against a real transactional H2 database.
7. Prepared local run commands, API requests, architecture diagrams, and a video outline so a
   reviewer can reproduce the behavior.

AI output was treated as a draft: database constraints, transaction boundaries, error behavior,
and tests were reviewed as engineering artifacts rather than accepted from prose alone.

