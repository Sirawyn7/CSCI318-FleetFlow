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

## Rationale

Keeping the guard in `Assignment` means the attempt count is saved even if the service restarts. Using the same row lock also prevents multiple triggers from starting duplicate reassignment attempts. The pending event is stored in the same record, so if publishing fails, it can be retried later. Since each delivery only allows one automated reassignment attempt, there’s no need for extra workflow or outbox tables.

## Implementation

- `Assignment` persists the attempt, courier transfer, pending exception and escalation
  state. Row locking serializes triggers; versioning prevents concurrent load
  acknowledgements from overwriting the guard. The attempt is claimed in a short
  transaction before the agent call, so overlapping triggers cannot call it twice.
  A crash after claiming still consumes the budget and leaves a pending exception.
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
  Escalation selects only active, pending, attempted, un-escalated rows at least 600
  seconds old, then rechecks under lock. Repeated base signals preserve a more
  specific pending reason such as `COURIER_OFFLINE: NO_ELIGIBLE_COURIER`.
- `GET /api/dispatch/exceptions` exposes the durable operator queue with reasons and
  timestamps. Delivery closure removes the live queue entry.
- Fleet transfers load once and handles duplicate or reordered events. Tracking keeps
  the delivery ID and history, updates courier/speed, and refreshes both courier
  snapshots. Monitoring excludes pre-transfer GPS pings and old movement history.

## Consequences and limitations

- No new dependencies, event names or synchronous Dispatch-to-Fleet calls.
- Existing assignments need lifecycle-event replay to populate active/terminal state.
  Back up existing H2 databases before schema upgrades.
- Reassignment is a logical transfer; physical handoff confirmation and manual operator
  commands remain outside this implementation. The notifications/cancellation PR
  was merged into main in `625ce77` and is included in this branch. Notification
  consumes `OrderReassigned`. Cancelled assignments stop exception handling and
  release both current- and previous-courier reservations; Fleet preserves terminal
  cancellation tombstones when late reassignment events arrive.
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

On 2026-10-12, the full reactor non-integration suite passed: **234 tests**, including
Dispatch's 76 tests. Added coverage verifies that an offline consumer and recovery
check share one agent call while the first call is in flight, that the no-candidate
reason survives repeated offline checks, and that escalation selection respects
the cooldown boundary and excludes already escalated or closed deliveries.

`DeliveryExceptionIntegrationTest` **passed (1 test, no failures/errors/skips)** on
2026-10-12 using Docker Desktop and a real Testcontainers Kafka broker
(`confluentinc/cp-kafka:7.5.0`). This supersedes the earlier Docker-unavailable result.
Commands: `mvn test '-Dtest=!*IntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'`
and `mvn -pl dispatch-service -am test '-Dtest=DeliveryExceptionIntegrationTest'
'-Dsurefire.failIfNoSpecifiedTests=false'`.

Review follow-up evidence: [prompt 0015](../prompts/0015-uc4-review-corrections.md).
The original implementation prompt is unavailable; prompt 0015 records the review follow-up.

On 2026-10-13, after reconciliation with the merged notifications/cancellation
changes, the full reactor non-integration suite passed **249 tests**, with no
failures, errors or skips (Dispatch 84, Fleet 43). New regressions cover cancellation
during an agent call, late exception signals, release of both courier reservations,
suppression of pending reassignment publication, and Fleet cancellation tombstones
receiving late assignment/reassignment events. Command: `mvn -o test
'-Dtest=!*IntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'` using the
existing local dependency cache. Kafka integration tests were not rerun in this session.
