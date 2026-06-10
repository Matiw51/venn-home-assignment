# Venn Back-End Home Assignment

A Spring Boot application that processes fund load attempts and enforces velocity limits per customer.

## How to Run

```bash
mvn spring-boot:run
```

Reads from `input.txt` in the working directory, writes results to `output.txt`.

## How to Test

```bash
mvn test
```

## Assumptions

The following behaviors are not explicitly defined in the spec and were decided as part of the implementation:

**Zero and negative amount loads are silently ignored.** No response is written to the output and no record is persisted. They do not count against the daily load count or any amount totals. The spec defines velocity limits in terms of real monetary value, so a $0.00 load has no meaningful place in that model.

**Declined loads do not count against any limit.** Neither the daily load count nor the daily/weekly amount totals are affected by a declined attempt. Only accepted loads are tracked. This follows from the principle that a limit should reflect actual funds loaded, not attempted.

**Duplicate load ID detection is scoped per customer.** Two different customers may share the same load ID — each is processed independently. Only when the same customer submits the same load ID more than once is the repeat silently ignored (no output).

**Week boundaries follow ISO-8601.** Weeks start on Monday at 00:00:00 UTC and end at Sunday 23:59:59 UTC, consistent with the spec's explicit statement.

**Day boundaries are midnight UTC.** A load timestamped `2018-01-01T23:59:59Z` and one at `2018-01-02T00:00:00Z` are on different days.
