# Prompt 0006 — Correcting the Order slice after review

**Date:** 2026-09-10
**Owner:** Tenzin (Order Service)
**Status:** Accepted after modifications
**Used for:** UC1 review fixes and ADR 0006 implementation.

## Prompt (verbatim excerpt)

```text
- `OrderController` returns the raw domain `Order` as the JSON response body — no response DTO, unlike Fleet's `RegisterCourierResponse` pattern.
```

The same request identified persistence inconsistency, missing transaction boundaries,
unreachable cancellation, absent tests, missing output bindings, ADR naming, and the
unfinished human collaboration reflection. It requested Docker/WSL Kafka validation.

## Generated changes (factual record)

- Direct aggregate JPA mapping following Fleet, with an ordered status-history collection.
- Application transactions and checked synchronous event publication.
- Cancellation HTTP endpoint, response DTOs, and input validation.
- Domain, HTTP, persistence rollback, publisher, and real Kafka tests.
- ADR filename correction and repeatable validation commands in README.

## Human reflection

I kept the original structure but the review showed that the implementation was lacking some critical components, such as the cancel endpoint and tests. Changes have been sought in order to adhere to the conventions of the project and ensure data integrity. New tests included testing for creating orders, cancelling orders, saving orders to the database, and publishing to Kafka. This made me realise that Ai generated code needs to be checked against the requirements and tested.