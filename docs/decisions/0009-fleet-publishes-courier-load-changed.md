# ADR 0009 — Fleet publishes CourierLoadChanged

**Status:** Accepted
**Owner:** Steven
**Date:** 2026-09-16

## Context

FR7 (`docs/requirements-outline.md` §4, UC3) requires deterministic courier
selection "by proximity, capacity and zone." While building FR7 in dispatch-service,
it became clear that Dispatch's `courier_availability_view` only ever receives the
*static* capacity ceiling from `CourierRegistered` (FR4) — the courier's real-time
active-order count is never published anywhere. That count already exists: Fleet's
`Courier` aggregate maintains `activeOrderCount` via `assignOrder()`/`completeOrder()`
per ADR 0002/0008, consuming `OrderAssigned`/`DeliveryCompleted` internally. It is
simply never emitted as an event, so no other service — Dispatch for FR7, or the
FR8 agent later — can see whether a courier is actually near its ceiling versus
merely registered with one.

This is a different kind of gap than the one FR7 hit with zone matching: zone data
genuinely doesn't exist anywhere in the system for an order. Capacity data *does*
exist, authoritatively, in Fleet — it's just unpublished.

## Options considered

1. **Leave it unpublished; Dispatch (and later FR8) reasons only over the registered
   ceiling.** Zero new work, but Dispatch can assign to a courier already at their
   ceiling, which partially defeats FR6's purpose, and permanently caps FR8's
   reasoning to a number that's usually wrong.
2. **Dispatch calls Fleet synchronously to check current load at assignment time.**
   Directly conflicts with the locked §7 decision that Dispatch never calls Fleet
   synchronously — same reasoning as ADR 0001 rejecting the equivalent option for
   Order. Rejected for the same reason.
3. **Fleet publishes a new `CourierLoadChanged` event whenever `activeOrderCount`
   changes; Dispatch consumes it into `courier_availability_view` (extending the
   existing read model), consistent with how every other piece of Fleet state
   already reaches Dispatch (`CourierRegistered`, `CourierAvailabilityChanged`,
   `CourierLocationUpdated`).** No new inter-service coupling pattern — same
   locked-decision-2 read-model approach already in use.

## Decision

Add `CourierLoadChanged` to the event catalogue now. Fleet gains a consumer path
that's already implied by FR6 regardless of this ADR: it consumes `OrderAssigned`
to increment the count and `DeliveryCompleted` to decrement it (the increment/
decrement logic itself, `assignOrder()`/`completeOrder()`, already exists on
`Courier` — only the publish side and the two consumers are new). It publishes
`CourierLoadChanged(EventEnvelope envelope, UUID courierId, int activeOrderCount, int capacityCeiling)`
on topic `fleetflow.fleet.courier-load-changed` whenever the count changes. Dispatch
consumes it into `courier_availability_view`, extending the existing read model —
no synchronous call to Fleet, consistent with locked decision 2.

The capacity data is authoritative in Fleet and simply unpublished, unlike the zone
gap where no part of the system held the data at all. Leaving it unpublished means
Dispatch can assign to a courier already at their ceiling, which partially defeats
FR6, and constrains FR8 to reasoning over a registered ceiling rather than actual
load. Fleet must maintain the count for FR6 regardless, so publishing the change is
a small increment on required work, not a new subsystem.

The contract record and topic constant are added to `fleetflow-contracts` now,
alongside this ADR, so the catalogue is accurate as soon as the gap is known rather
than left stale until the wiring happens. Wiring Fleet's publish side and Dispatch's
consumption into `courier_availability_view` is explicitly **not** part of FR7 —
that belongs to a separate Fleet/Dispatch slice, so FR7 isn't blocked waiting on it.
FR7 ships using the static capacity ceiling as a tie-breaker in the meantime.

## Consequences

- `fleetflow-contracts` gains a 16th event; `docs/AGENTS.md` and
  `docs/requirements-outline.md` §6/§6.1 are updated to list it with no consumer
  wired yet, so the catalogue doesn't silently drift from the code.
- Fleet's `couriers` table needs no new column — `activeOrderCount` already exists
  per ADR 0002/0008; this ADR only adds a publish step when it changes.
- A follow-up slice must: (a) call the new publisher from `Courier.assignOrder()`/
  `completeOrder()`'s call sites in Fleet, and (b) add a `CourierLoadChanged`
  consumer in dispatch-service that updates `courier_availability_view`'s capacity
  fields to reflect real remaining capacity instead of the static ceiling. Until
  that lands, FR7's capacity criterion remains a known simplification (ceiling-only,
  not true remaining capacity) — documented in FR7's own implementation rather than
  silently assumed.
