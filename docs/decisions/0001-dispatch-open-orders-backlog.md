# ADR 0001 — Dispatch's view of open orders

**Status:** Accepted
**Owner:** Steven
**Date:** 2026-09-07

## Context

FR8 requires the dispatch agent to reason "over open orders, courier positions, capacity and SLA pressure" (`requirements-outline.md` §4, UC3). `docs/AGENTS.md` line 104 confirms the agent "fires on `OrderPlaced`." Both assume Dispatch has some view of the open-order backlog to reason over.

§8's Data Ownership table gives Dispatch exactly three tables: `assignments`, `assignment_decisions`, `courier_availability_view`. None of them is a backlog of open orders. Dispatch has a read model for courier state (§7 locks this explicitly) but no equivalent for order state — this was never decided.

This has to be settled before FR8 implementation starts in Week 7; discovering it mid-agent-build would mean redesigning the agent's tool-call surface under time pressure.

## Options considered

1. **Derive on demand from `assignments`.** Query `assignments` for orders with no active assignment row. Cheapest to build, but "no assignment" doesn't distinguish a genuinely open order from one Dispatch has never heard of (e.g. a cancelled order, or one it failed to receive). Also gives the agent no order attributes to reason over — no SLA pressure, no package/zone info — since `assignments` was never meant to hold order detail.

2. **Maintain a projection from `OrderPlaced` / `OrderAssigned` (and `OrderCancelled`, `OrderReassigned`, `AssignmentFailed`).** A `open_orders_view` table, analogous in spirit to `courier_availability_view` — Dispatch already owns the pattern of a derived read model built from consumed events (§7). Insert on `OrderPlaced`, remove on `OrderAssigned` / `OrderCancelled`. This is consistent with the existing Dispatch → Fleet architecture and requires no new inter-service coupling.

3. **Query Order over REST at decision time.** Simplest mental model, but **directly conflicts with the locked §7 decision that "Dispatch never calls Fleet synchronously"** — the same reasoning (avoid distributed coupling, tolerate eventual consistency, keep Dispatch assigning even if the other service is down) applies just as strongly to Order as it does to Fleet. Listed for completeness; recommend against.

## Decision

We're going with option 2: Dispatch keeps its own table of open orders (open_orders_view), built by listening to OrderPlaced, OrderAssigned, OrderCancelled etc., instead of trying to work it out from assignments or asking Order directly over REST.

We've picked this because we already do the exact same thing for courier availability (courier_availability_view listens to Fleet's events), so this keeps things consistent instead of having two different patterns for basically the same problem. Querying assignments felt fragile, an order with no assignment row doesn't necessarily mean it's actually open, it could just mean we never got told about it.

## Consequences

This means Dispatch now has to consume more events than we originally planned (OrderPlaced at minimum, plus OrderCancelled/OrderAssigned to remove things from the view once they're no longer open). We'll need to add open_orders_view to the data ownership table in the requirements doc. It also means the agent's "open orders" view could be a few seconds stale, same as the courier data already is, which is being treated as an accepted risk
