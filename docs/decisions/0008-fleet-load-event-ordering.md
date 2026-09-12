# ADR 0008 — Fleet load event ordering

**Status:** Accepted
**Owner:** Tenzin
**Date:** 2026-09-12

## Context

ADR 0002 places the capacity-constrained active-order count on Courier. Assignment
and completion arrive on separate topics, so completion can arrive first. Event-ID
deduplication alone also cannot detect two event IDs referring to the same order.

## Implementation proposed (AI-generated)

Keep `active_order_count` on `couriers`, as accepted in ADR 0002. Add an infrastructure
ledger, `courier_order_load`, keyed by order ID with courier ID and completion flag.
It records which orders contribute to the count; it does not own assignments.
A completion without a known assignment creates a completed row without changing
the count. A later assignment for that order has no effect.

Use a pessimistic courier lock and one transaction for the counter and both ledgers.
Optimistic versioning also guards writes through other Courier persistence paths.
Capacity conflicts roll back and use the Kafka retry/dead-letter path.

## Decision

Active order count is kept for the Courier and a new ledger courier order load is introduced to record orders. Kafka may generate duplicate events or even send an event before the order has been assigned, and the validation of event ID is not enough to handle these cases. Order ledger is selected because it guarantees that the count will not change in an invalid way.

## Consequences and limits

Completed rows must be retained for late-event protection. Retention/archival needs
a replay policy before rows can be removed. Reassignment is still excluded by
ADR 0002; a different courier for an existing order is rejected for investigation.
Fleet cannot retroactively reject a Dispatch assignment: Dispatch must separately
enforce its assignment eligibility rules.

Profile deactivation uses an independent boolean, preserving FR5 shift status.
Deactivation requires zero active orders. No new event names are introduced;
profile/deactivation propagation to Dispatch needs a separately agreed contract.
