# ADR 0007 — Order items and write consistency

**Status:** Accepted
**Date:** 2026-09-10
**Owner:** Tenzin

## Context

Review identified missing `order_items` storage, database constraints checked only after
publishing, and concurrent cancellation requests able to publish duplicate events.
Requirements name the items table but do not specify its fields.

## Implementation

- Model items as immutable description/quantity values owned by Order, stored in an
  ordered JPA element collection. Description and quantity are the accepted fields for this slice.
- Keep package details as the package-level summary. Requests without items create one
  item with that description and quantity 1; explicit empty lists and invalid items fail.
- Preserve old orders without inventing item quantities: existing rows may return no items.
- Flush aggregate writes before publishing to detect database constraint failures early.
- Acquire a pessimistic root-row lock before cancellation and hold it through commit.
  Read-only queries do not request that lock.

## Rationale

Order items are part of an order, so they should be stored and checked together with the order instead of being managed separately. This keeps the order data together and follows the existing Order structure.

Flushing the database before publishing helps find database errors before a Kafka event is sent. Locking the order during cancellation also prevents two requests from cancelling the same order at the same time and sending duplicate events.

The downside is that another cancellation request may have to wait while the order is locked. This approach improves consistency, but it does not guarantee that the database and Kafka will always succeed together.

## Consequences

The lock is held during synchronous publication, so broker latency can delay contenders.

Flushing is not an atomic database/Kafka commit. A failure after a successful publication
can still leave an event without its database change. An outbox and consumer deduplication
would require a separate design; this change does not claim exactly-once delivery.
Lifecycle event consumers remain subject to the requirements' team-confirmation rule.
