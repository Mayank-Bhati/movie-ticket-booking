# CLAUDE.md

Context and conventions for working on this repository with Claude Code.

## Project

Movie ticket booking backend: cities/theaters/screens/shows, seat-level booking with time-bound
holds, tiered pricing, discount codes, mock payment, refunds under a configurable policy, and
non-blocking notifications. Concurrent bookings must be serialized so a seat is never
double-allocated. See `README.md` for the full feature list and design notes.

## Tech stack

- Java 21, Spring Boot 4.1 (Maven, via the `./mvnw` wrapper)
- Spring Web MVC, Spring Security (stateless JWT), Spring Data JPA, Bean Validation
- MySQL (default) / H2 (tests and the `demo` profile), schema managed by Flyway
- Lombok (`@Getter`/`@Setter` on entities only), JJWT, JaCoCo

## Commands

```bash
./mvnw test                                          # run all tests + coverage report
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # run on H2, no DB setup
DB_PASSWORD=... ./mvnw spring-boot:run               # run on local MySQL
```

Coverage report: `target/site/jacoco/index.html`.

## Architecture & conventions

- **Package by feature**: `auth`, `booking`, `catalog`, `pricing`, `refund`, `notification`,
  `security`, `web`, plus `entity`, `repository`, `config`. Keep new code in the matching feature
  package.
- **Layering**: controller → service → repository. Controllers are thin; business logic lives in
  services; persistence in Spring Data repositories.
- **Constructor injection only** (no field injection).
- **DTOs are Java records**, grouped per feature (e.g. `BookingDtos`). Never expose entities from
  controllers.
- **Entities** use Lombok `@Getter`/`@Setter`; enums are nested in their owning entity. Domain
  behaviour that belongs to an entity lives on it (e.g. `Seat.label()`).
- **Errors**: throw `ApiException` with the right status; `GlobalExceptionHandler` renders a
  consistent JSON body. Validate request DTOs with Bean Validation annotations.
- **Transactions**: write paths are `@Transactional`; read paths that touch lazy associations are
  `@Transactional(readOnly = true)` (`open-in-view` is disabled).
- **Concurrency**: seat contention is serialized with `PESSIMISTIC_WRITE` locks on `ShowSeat` rows,
  ordered by id. Limited discount codes are locked the same way during redemption.
- **Schema changes** go through a new Flyway migration in `src/main/resources/db/migration`
  (`V{n}__description.sql`); never rely on `ddl-auto` (it is set to `validate`).

## Testing

- Unit tests (Mockito) for pure logic: pricing, discounts, refund policy.
- Integration tests (`@SpringBootTest` on H2) for the flows: auth/RBAC, full booking journey,
  concurrent double-booking, discount-cap under concurrency, hold expiry, payment-failure rollback.
- HTTP tests extend `IntegrationTest` (MockMvc + helpers) and are `@Transactional` for isolation;
  thread-based concurrency tests commit and use unique seed data.
- Keep coverage ≥ 90% line. Run `./mvnw test` before every commit.

## Development workflow used

Built in small, reviewable stages, each verified by running the app and exercising the endpoints,
then committed separately: scaffold → schema/entities → auth/RBAC → catalog → pricing/discounts →
holds/booking → cancellation/refunds → notifications → tests → hardening & docs. Commit messages are
short and descriptive.
