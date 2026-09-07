# ADR 0002 — Where Fleet's active-order count lives

**Status:** Accepted
**Owner:** Steven
**Date:** 2026-09-07

## Context

FR6 (`requirements-outline.md` §4, UC2) requires Fleet to "maintain an active-order count against a capacity ceiling to prevent overloading." That count can only change when a courier is given a new delivery or finishes one — i.e. Fleet must consume `OrderAssigned` (increment) and `DeliveryCompleted` (decrement), and arguably `OrderReassigned` / `AssignmentFailed` (decrement the losing courier).

No document currently says Fleet consumes anything at all — §7's "Inter-Service Communication" section only describes Dispatch consuming Fleet's events, not the reverse. §8's Data Ownership table gives Fleet `couriers`, `vehicles`, `availability_windows`, `courier_locations` — none of which has a field for a live count. This surfaced while building the §6 publisher/consumer matrix (see `requirements-outline.md` §6.1): FR6 was already assumed satisfied, but nothing traced how the count is actually kept current.

This also means Fleet needs a `processed_events` idempotency ledger (see the `processed_events` note added to §8), since it becomes an event consumer for the first time.

## Options considered

1. **A column on `couriers`** — e.g. `active_order_count INT`, updated in the same transaction as the consuming logic. Simplest schema; the count lives directly on the aggregate it constrains (`Courier`, per the capacity-ceiling invariant), which is arguably the cleanest DDD fit — this is exactly the kind of invariant an aggregate root is supposed to enforce.

2. **A separate derived `courier_load` table** — one row per courier, rebuildable from the event stream, kept apart from the source-of-truth `couriers` table. Mirrors the source-of-truth-vs-derived-read-model distinction §8 already calls out for Dispatch and Tracking, which is explicitly assessed. Slightly more moving parts for a single integer.

Deriving the count on demand (query `assignments` when needed, as in ADR 0001's option 1) is **not available here** — Fleet does not own `assignments`; only Dispatch does. Fleet has no way to compute this without consuming events.

## Decision

We're going with option 1: just add an active_order_count column straight onto the couriers table, rather than making a whole separate table for it.

Reasoning: it's one number per courier, not really a "read model" in the same way the other derived tables are, it's more just an attribute of the courier that changes over time, so it made more sense to us to keep it on the courier itself rather than invent a new table for a single integer.

## Consequences

Fleet now has to consume events for the first time (OrderAssigned to add 1, DeliveryCompleted to subtract 1), which it didn't do before, so it also needs its own processed_events table like the other services that consume events. For now we're leaving OrderReassigned/AssignmentFailed out of scope until later.
