# ADR 0006 — Order status history persistence

**Status:** Implemented following UC1 review; team rationale pending

**Owner:** Tenzin

**Date:** 2026-09-08

## Context

UC1 – Manage Delivery Order includes FR2, which requires the Order service to provide the current state of an order and timestamps as it moves through statuses such as `PLACED`, `ASSIGNED`, `IN_TRANSIT`, `DELIVERED`, and `FAILED`.

Before the review fix, the Order implementation stored the current `OrderStatus` and `createdAt` time on the order. This exposed the current state but did not keep a record of previous status changes.

The project design also identifies `order_status_history` as data owned by the Order bounded context. The review requested persistence consistent with that ownership.

## Options considered

1. **Store only the current status on `orders`.**

   Keep the existing `status` and `createdAt` fields without a separate history table.

   This is simpler to implement, but previous status changes and their timestamps would not be available.

2. **Store the current status on `orders` and keep status changes in `order_status_history`.**

   The `orders` table continues to contain the current status for simple order queries. A separate `order_status_history` table records each status and the time that status was reached.

   For example:

   ```text
   order_id | status      | changed_at
   ---------------------------------------
   abc123   | PLACED      | 10:00
   abc123   | ASSIGNED    | 10:05
   abc123   | IN_TRANSIT  | 10:20
   abc123   | DELIVERED   | 11:15
   ```

## Implementation

Option 2 — keep the current status on `orders` and maintain a separate `order_status_history` table.

The review fix maps `Order` directly with JPA, matching Fleet's aggregate mapping.
`OrderStatusChange` is an immutable embeddable value in an ordered element collection,
owned by `Order`, persisted in `order_status_history`. Both creation and cancellation
write history inside the application transaction. The API maps these values to DTOs.
No independent history repository is needed because history belongs to the aggregate.

## Rationale

To be completed by Tenzin if this option is accepted.

Points to consider in your own reasoning:

* FR2 requires status information and timestamps.
* Keeping the current status on `orders` makes normal order queries simple.
* Keeping a separate history allows previous status changes to be retrieved.
* The table remains owned by the Order service, maintaining the bounded-context boundary.

## Consequences

* JPA persists the aggregate-owned history collection in a separate table.
* Creating an order should record the initial `PLACED` status and timestamp.
* Future status transitions must add another history entry; inferred event consumers remain outside this slice.
* Querying an order can provide both its current status and its status history.
* This adds some implementation work compared with storing only the current status.
