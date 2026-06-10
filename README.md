# Venn Back-End Home Assignment

A Spring Boot application that processes fund load attempts and enforces velocity limits per customer.

## How to Run

```bash
mvn spring-boot:run
```

Reads from `input.txt` in the working directory, writes results to `output.txt`.

## How to Test

Unit and slice tests only (fast):
```bash
mvn test
```

Full test suite including integration tests:
```bash
mvn verify
```

## Testing

The spec requires production-ready code, so beyond the basic happy path the test suite covers edge cases including exact daily and weekly limit boundaries, midnight UTC and Monday week resets, declined loads not counting against limits, duplicate load ID scoping per customer, and zero-amount load filtering.

Tests are split into two layers. `VelocityServiceTest` uses `@DataJpaTest` to test the business logic against a real JPA layer without loading the full Spring Boot context — these run on every `mvn test`. Integration tests under `integration/` use `@SpringBootTest` and run only on `mvn verify`.

## Assumptions

The following behaviors are not explicitly defined in the spec and were decided as part of the implementation:

**Zero and negative amount loads are filtered out.** No response is written to the output and no record is persisted. They do not count against the daily load count or any amount totals. The spec defines velocity limits in terms of real monetary value, so a $0.00 load has no meaningful place in that model.