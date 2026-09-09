# ADR 0007 — Order items and write consistency

**Status:** Implemented; item-field assumption awaits team review
**Date:** 2026-09-10
**Owner:** Tenzin (Order Service)

## Context

Review identified missing `order_items` storage, database constraints checked only after
publishing, and concurrent cancellation requests able to publish duplicate events.
Requirements name the items table but do not specify its fields.

## Implementation

- Model items as immutable description/quantity values owned by Order, stored in an
  ordered JPA element collection. Description and quantity are the initial field assumption.
- Keep package details as the package-level summary. Requests without items create one
  item with that description and quantity 1; explicit empty lists and invalid items fail.
- Preserve old orders without inventing item quantities: existing rows may return no items.
- Flush aggregate writes before publishing to detect database constraint failures early.
- Acquire a pessimistic root-row lock before cancellation and hold it through commit.
  Read-only queries do not request that lock.

## Technical rationale and consequences

Items remain within the Order aggregate and database, with no independently mutable
collection exposed. The request remains compatible with existing callers.
A row lock serializes competing cancellations before they check state or publish;
optimistic locking alone at commit would occur too late to prevent duplicate publication.
The lock is held during synchronous publication, so broker latency can delay contenders.

Flushing is not an atomic database/Kafka commit. A failure after a successful publication
can still leave an event without its database change. An outbox and consumer deduplication
would require a separate design; this change does not claim exactly-once delivery.
Lifecycle event consumers remain subject to the requirements' team-confirmation rule.
