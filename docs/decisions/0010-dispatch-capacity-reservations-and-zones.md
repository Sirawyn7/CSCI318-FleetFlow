# ADR 0010 — Dispatch agent boundary, zones and capacity reservations

**Status:** Accepted
**Owner:** Tenzin
**Date:** 2026-09-17


## Context

FR7 had nearest-courier selection but no order-zone comparison and only static
capacity. FR8 needs actual load, a read-only tool surface, validated output and a
five-second deadline. ADR 0009 authorizes publishing/consuming Fleet's load event.
Using the reported count alone still allows repeated assignments before Fleet
processes the first `OrderAssigned`.

## Implementation proposed (AI-generated)

- Infer zone eligibility from configurable geographic rectangles and existing
  pickup coordinates. No external map service or new order event field is needed.
  Treat unknown zones as ineligible; keep sample rectangles clearly labelled.
- Reserve capacity in Dispatch's own assignment rows until Fleet acknowledges
  each order. Extend `CourierLoadChanged` with a monotonic courier load version
  and acknowledged order ID. Ignore stale counts, but reconcile every known order.
- Build immutable snapshots and call the agent outside a transaction. At commit,
  lock only the target order and selected courier, revalidate current eligibility,
  then reserve capacity. A stale choice falls back to refreshed rule ranking; each
  candidate attempt commits or releases its locks before another is tried. Load
  acknowledgements use the same courier lock. No database operation is exposed as
  an agent tool. Keep eight workers with no task queue and a five-second overall
  deadline; bound the model/tool loop separately to three tool rounds. The HTTP
  adapter waits on asynchronous I/O with a deadline so unresponsive I/O cannot
  indefinitely occupy an agent worker.
- Use minimum-inclusive, maximum-exclusive zone bounds so adjacent rectangles
  have one owner for shared edges. Validate malformed load values before ignoring
  stale versions. Preserve authoritative load and log acknowledgements without a
  corresponding local assignment instead of sending historical data to the DLQ.
- Validate recommendations with the same eligibility set used by rules. Persist
  the chosen path and written explanation. Neither late results nor invalid model
  output can create assignments.

## Options considered

1. **Keep FR7 as the only assignment logic** — deterministic nearest-courier
   selection is simple and explainable, but it does not use Fleet's live load data,
   cannot reason about zone fit in the same way as the bounded agent, and cannot
   optimize across multiple practical constraints.
2. **Use the bounded agent as the only path** — this would maximize optimization,
   but it introduces unacceptable risk if the model is slow, returns invalid
   recommendations, or can ignore local capacity and zoning rules.
3. **Use the bounded agent as the primary path with FR7 as a validated fallback**
   — this keeps the optimization benefit of FR8 while preserving deterministic,
   explainable recovery when the agent cannot complete safely.

## Decision

We chose option 3. The agent will still be the main method used for dispatching, but any assignment it makes must follow the same zone, capacity, and reservation checks as the normal deterministic rules.

If the agent times out, fails validation, gives an invalid result, or cannot find an eligible courier, Dispatch will fall back to the deterministic FR7 selector.

This allows us to keep the benefits of the FR8 agent while making sure it cannot assign an unsuitable courier or exceed a courier’s remaining capacity.

This decision was accepted by Tenzin on 17 September 2026.


## Consequences

The design preserves the locked local-read-model integration and primary-agent
precedence. It prevents capacity oversubscription caused by Dispatch's own pending
assignments. Reordered acknowledgements may conservatively block a free slot until
all earlier acknowledgements arrive. The AI wait no longer holds database locks;
transactional publication still has the existing database/Kafka crash window.
Historical acknowledgements can advance the load projection without a local
assignment, but historical assignment/reservation data still needs reconciliation.

Implementation refined after the 2026-09-19 review; the team's decision above is
preserved. Regression tests cover concurrent progress during a slow agent call,
commit-time revalidation, cancellation, orphan acknowledgements, zone edges,
invalid versions, concurrent registration and unresponsive HTTP transport.

The locked model default is unchanged pending the team's response to its retirement.
