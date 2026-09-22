# Development Tools and AI Usage

I used AI as an engineering assistant during development, following the same workflow I use for
design discussions and implementation reviews:

1. I started with the product requirements and discussed the high-level architecture, consistency
   boundaries, and key trade-offs with AI.
2. I worked through the low-level design: modules, entities, table relationships, API contracts,
   seat state transitions, and concurrency behavior.
3. Once the design was clear, I asked AI to generate implementation drafts in small capabilities
   rather than producing the entire system as one prompt.
4. I reviewed the generated code, validated transaction boundaries and database constraints, and
   used compiler and test feedback to correct the implementation.
5. I verified the critical flows with integration tests, including a concurrent race for the same
   seat, payment rollback, hold expiry, idempotent booking, refunds, and role-based access.

Tools used during this process:

- Java 21, Spring Boot, Spring JDBC, Spring Security, Flyway, H2, and PostgreSQL.
- JUnit, MockMvc, AssertJ, and JaCoCo for testing and coverage.
- Maven and Git for builds and version control.
- AI-assisted requirement analysis, architecture discussion, implementation drafting, and review.
