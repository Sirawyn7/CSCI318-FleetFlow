# ADR 0017 — UC4 durable reassignment guard and delivery transfer

**Status:** Accepted
**Owner:** Tenzin
**Date:** 2026-10-11

## Context

FR9 requires reassignment when a courier goes offline or an SLA breach is predicted,
publishing `OrderReassigned`. The locked guard permits one automated attempt per
delivery, followed by a 10-minute cooldown before unresolved exceptions escalate.
Fleet and Tracking also need to transfer load and delivery monitoring.

## Options considered

1. **In-memory guard:** simple, but restarts or multiple instances reset the budget.
2. **Persist state on Assignment:** keeps the guard and pending publication together,
   protected by locking and a stable event ID.
3. **Separate workflow and outbox tables:** more flexible, but adds complexity for a
   workflow that allows only one retry.

## Decision

Persist the reassignment guard and pending `OrderReassigned` event on `Assignment`.
Allow one automated attempt per delivery, then hold unresolved exceptions for a
10-minute cooldown before escalating to the operator queue. Fleet transfers the
courier load, while Tracking preserves the existing delivery ID and history.

Accepted by Tenzin. Personal acceptance rationale remains for Tenzin to add.

## Implementation

- `Assignment` persists the attempt, courier transfer, pending exception and escalation
  state. Row locking serializes triggers; versioning prevents concurrent load
  acknowledgements from overwriting the guard.
- The first attempt consumes the budget even if no replacement is available. Unresolved
  exceptions escalate after 600 seconds. Successful reassignment only escalates if
  another exception occurs; it never grants another retry.
- The bounded agent remains primary, with rule fallback. Selection uses the current
  courier's last known position as the handoff point and rechecks capacity under lock.
  The outcome, path and justification are recorded in `assignment_decisions`.
- Pending `OrderReassigned` publication is stored on the assignment as a transactional
  outbox. Retries retain the same event ID, allowing consumer deduplication.
- `DeliveryStarted` identifies active deliveries; arrival and completion close exception
  handling. Stale risks from a previous courier are ignored. A recovery sweep retries
  publication, detects offline couriers and escalates due exceptions after restarts.
- `GET /api/dispatch/exceptions` exposes the durable operator queue with reasons and
  timestamps. Delivery closure removes the live queue entry.
- Fleet transfers load once and handles duplicate or reordered events. Tracking keeps
  the delivery ID and history, updates courier/speed, and refreshes both courier
  snapshots. Monitoring excludes pre-transfer GPS pings and old movement history.

## Consequences and limitations

- No new dependencies, event names or synchronous Dispatch-to-Fleet calls.
- Existing assignments need lifecycle-event replay to populate active/terminal state.
  Back up existing H2 databases before schema upgrades.
- Reassignment is a logical transfer; physical handoff confirmation, manual operator
  commands and reassignment notifications remain outside this implementation.
- Services remain eventually consistent. Capacity conflicts or missing prerequisites
  may require DLQ investigation and replay. Fleet acknowledgements and Tracking
  snapshots retain their existing publication/transaction limitations; replay and
  snapshot recovery repair partial failures.
- Worker downtime or agent calls can delay escalation beyond ten minutes, but cannot
  authorize another reassignment. Terminal deliveries are not reopened.

## Validation

On 2026-10-11, **191 local tests passed** across regression and follow-up runs
(Fleet 39, Dispatch 74, Tracking 78). Coverage includes the 599/600-second boundary,
concurrent triggers, capacity reservations, publication retries, event ordering,
rollback and delivery identity preservation.

`DeliveryExceptionIntegrationTest` covers offline/SLA events and reassignment through
real Kafka. It compiled but could not start because Docker was unavailable, so broker
behavior remains unverified on this machine.
