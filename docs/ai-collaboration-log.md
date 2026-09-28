# AI Collaboration Log

Running chronological log of AI-assisted decisions on this project, for Report Section 5 (AI-Driven Development Process).

**Rule (see `/docs/AGENTS.md`):** the assistant may capture raw material — what was prompted, what was generated — but the *decision and rationale* must be written by the team member who made the call. That judgment is where Section 5's marks are. Do not paste in an assistant-authored justification and call it done.

Target: roughly one entry per person per working session, ~15–25 entries by Week 11. Log it as part of your own slice's definition of done, not as a shared task to defer.

## Entry format

```
## YYYY-MM-DD — Short title of the decision (Name)
**Prompt/context:** What was asked of the assistant, and why.
**Proposed by agent:** What the assistant proposed or generated.
**Decision:** Accepted / Rejected / Modified.
**Rationale:** Why, in your own words. Reference the specific bounded-context,
DDD, or architectural principle at stake if there is one. If this decision has a
full ADR in `docs/decisions/`, name the specific file rather than saying "see the
ADR" — e.g. "see docs/decisions/0001-dispatch-open-orders-backlog.md".
```

---

## 2026-09-07 — Dispatch open-orders backlog (Steven)
**Prompt/context:** Asked the assistant to build a publisher/consumer matrix for the
event catalogue, since §6 only documented publishers. That exercise surfaced that
Dispatch had no table to hold open orders, despite FR8 having the dispatch agent
reason over "open orders" and fire on `OrderPlaced`.
**Proposed by agent:** Three options for where Dispatch's open-order data comes
from, written up in `docs/decisions/0001-dispatch-open-orders-backlog.md`.
**Decision:** Accepted option 2 — a derived `open_orders_view` projection, matching
the existing `courier_availability_view` pattern.
**Rationale:** See `docs/decisions/0001-dispatch-open-orders-backlog.md` for full
reasoning.

---

## 2026-09-07 — Fleet active-order count (Steven)
**Prompt/context:** The same publisher/consumer matrix exercise flagged that FR6
requires Fleet to track an active-order count against a capacity ceiling, but Fleet
had no field for it and wasn't documented as consuming any events at all.
**Proposed by agent:** Two options for where the count lives, written up in
`docs/decisions/0002-fleet-active-order-count.md`.
**Decision:** Accepted option 1 — an `active_order_count` column directly on
`couriers`, rather than a separate table.
**Rationale:** See `docs/decisions/0002-fleet-active-order-count.md` for full
reasoning.

---

## 2026-09-07 — Spring Boot version pin for the scaffold (Steven)
**Prompt/context:** Asked the assistant to scaffold the parent POM. It flagged that
Spring Boot's current GA is major version 4 (4.1.1), a jump from the 3.x line, and
asked which to pin to before writing any POM.
**Proposed by agent:** Two options in docs/decisions/0003-spring-boot-version-pin.md —
Boot 4.1.1 + Spring Cloud 2025.1.2, or Boot 3.5.16 + Spring Cloud 2025.0.3.
**Decision:** Accepted option 2 — Spring Boot 3.5.16 + Spring Cloud 2025.0.3.
**Rationale:** See docs/decisions/0003-spring-boot-version-pin.md for full reasoning
(timeline risk on a fixed 6-week/3-person project outweighs using the newest release,
particularly given the two graded LangChain4j agents are scheduled for week 7).

---

## 2026-09-07 — fleetflow-contracts: Published Language vs Shared Kernel (Steven)
**Prompt/context:** Implementing Commit 2 of the approved scaffold plan — a
zero-dependency `fleetflow-contracts` module carrying the 15 locked §6 events, shared
across all five services.
**Proposed by agent:** Module design plus a self-review against the §6.1 consumer
matrix, written up in `docs/decisions/0004-fleetflow-contracts-published-language.md`.
**Decision:** Accepted option 3 — Published Language, not a Shared Kernel.
**Rationale:** See `docs/decisions/0004-fleetflow-contracts-published-language.md` for
full reasoning.

---

## 2026-09-08 — LLM model selection for FR8/FR16 (Steven)
**Prompt/context:** Closing the open LLM model decision from `requirements-outline.md`
§12.1, which was blocking FR8 and FR16 heading into week 7.
**Proposed by agent:** Three options (Gemini free tier, Groq free tier, self-hosted
Ollama) evaluated against the $0 budget constraint and FR8's locked 5-second dispatch
timeout, written up in `docs/decisions/0005-llm-model-selection.md`.
**Decision:** Accepted option 1 — Gemini (`gemini-2.0-flash`) via the free Google AI
Studio API.
**Rationale:** See `docs/decisions/0005-llm-model-selection.md` for full reasoning.

---

## 2026-09-08 — UC1 Manage Delivery Order implementation (Tenzin)
**Prompt/context:** Asked the assistant for help implementing UC1 – Manage Delivery Order in the Order service, covering order creation, querying an order, and cancelling an order. I also needed help understanding how the existing domain, application, API, and infrastructure layers should be used.
**Proposed by agent:** The assistant proposed an Order domain model using `Order`, `Address`, `OrderStatus`, and `OrderRepository`; an application service and `OrderEventPort`; JPA persistence classes in infrastructure; a REST `OrderController`; and an `OrderEventPublisher` for the existing `OrderPlaced` and `OrderCancelled` contracts. The assistant also helped diagnose compilation and Spring Boot startup errors while implementing these files.
**Decision:** Modified — I kept the original structure but requested implementation fixes and added new tests.
**Rationale:** I kept the layered structure but requested fixes to follow project conventions, protect data integrity, and ensure order creation and cancellation work correctly.

---

## 2026-09-12 — Fleet load event ordering for FR4/FR6 (Tenzin)
**Prompt/context:** Asked the assistant to implement UC2's FR4 and FR6, leaving
FR5 to my teammate.
**Proposed by agent:** Keep the active-order count on Courier and add an order
ledger to handle duplicate and out-of-order events; see
`docs/decisions/0008-fleet-load-event-ordering.md`.
**Decision:** Accepted the proposal to keep the active-order count on Courier and add a `courier_order_load` ledger.
**Rationale:** See `docs/decisions/0008-fleet-load-event-ordering.md` for the rationale.
**Prompt record:** `docs/prompts/0007-fleet-load-event-ordering.md`.

---

## 2026-09-16 — CourierLoadChanged event catalogue amendment
**Prompt/context:** While scoping FR7's capacity criterion, surfaced that Fleet's
real-time active-order count is never published, only the static registration
ceiling.
**Proposed:** Add a CourierLoadChanged event now, but leave the actual publish/
consume wiring for a later slice.
**Decision:** Accepted.
**Decision:** See ADR 0009 for full rationale — event added, wiring deferred to a
separate slice.

---

## 2026-09-16 — Assignment idempotency ledger deviates from processed_events
**Prompt/context:** The task specified reusing the processed_events ledger pattern
for FR7's idempotency check; analysis showed that keying on OrderPlaced's eventId
would collide with open_orders_view's own processed_events marker for the same
event, causing assignment to silently no-op on first delivery, not just redelivery.
**Decision:** Used assignment_decisions (keyed by orderId) as the idempotency
ledger instead.
**Rationale:** The same OrderPlaced event already gets marked "processed" by the
existing open-orders logic. If assignment checked the same row, it would always
look "already done" and never actually run, even the first time. Keying the check
on assignment_decisions instead avoids that clash and still follows the same
check-before-write pattern.

---

## 2026-09-19 — UC3 assignment reliability improvements (Tenzin)
**Prompt/context:** I asked the assistant to improve UC3 (FR7/FR8), covering
transaction boundaries during agent calls, load acknowledgements without a local
assignment, shared zone boundaries, and load-event validation. The work also
covered concurrent registration, duplicate events, HTTP timeouts, and regression
tests. See `docs/prompts/0009-uc3-review-corrections.md`.
**Proposed by agent:** Run the agent outside database transactions and revalidate
the selected courier in a short commit transaction; preserve authoritative Fleet
load updates when no local reservation exists; use exclusive maximum zone bounds;
and validate load values before checking staleness. Also address the registration
race, duplicate-event handling, HTTP deadlines, and regression coverage. The
Fleet and Dispatch suites passed all 100 tests, including Kafka integration tests.
Implementation details are recorded in
`docs/decisions/0011-dispatch-capacity-reservations-and-zones.md`.
**Decision:** Accepted the UC3 fixes described in prompt 0009, while keeping
option 3 from ADR 0011. The agent remains the main dispatch path, with FR7 as the
fallback. Both paths still have to follow the same zone, capacity, and reservation
checks.
**Rationale:** I wanted to keep the agent-based approach, but waiting for the
agent should not block other assignments or courier updates. Checking the courier
again before saving an assignment helps prevent using capacity that is no longer
available. The load-event and zone-boundary fixes also make the existing checks
more reliable. These changes improve the implementation without changing the
overall approach I accepted in
`docs/decisions/0011-dispatch-capacity-reservations-and-zones.md`.

---

## 2026-09-20 — FR11's internal topic and the FR10 re-keying (Landon)
**Prompt/context:** Building FR11's Kafka Streams topology, two non-obvious calls
came up: how the topology's KTable side gets materialized, and that FR10's
already-shipped CourierLocationUpdated publisher needed a partition key added for the
stream-table join to be co-partitioned correctly.
**Proposed by agent:** Two options each, written up in
docs/decisions/0010-fr11-internal-topic-and-rekeying.md — a new internal topic vs.
querying the deliveries table directly from inside the topology; re-keying FR10's
publisher vs. having Tracking re-publish a duplicate keyed stream.
**Decision:** Accepted option 1 in both cases — a new internal topic
(fleetflow.tracking.internal.active-delivery-by-courier), and re-keying FR10's
publisher directly rather than working around it.
**Rationale:** See `docs/decisions/0010-fr11-internal-topic-and-rekeying.md` for full
reasoning.


---

## UC3 documentation pointer

The accepted agent-first dispatch decision, capacity reservations, zone policy and
known limits are recorded in [ADR 0011](decisions/0011-dispatch-capacity-reservations-and-zones.md).
---

## 2026-09-23 — FR12's threshold defaults and the two-function split (Landon)
**Prompt/context:** Building FR12's geofence detection, two questions came up: what
counts as "near" vs "arrived" (requirements-outline.md left this as an open item --
"pick now, tune during testing"), and how to publish two different event types
(DeliveryNearDestination, DeliveryArrived) from what's logically one stream
evaluation, since a single Kafka Streams function has one output topic.
**Proposed by agent:** Three options for the multi-event-type problem, written up in
`docs/decisions/0011-fr12-geofence-thresholds-and-function-split.md` -- two separate
topology functions (one join each), a single join with a multi-output array return
via Kafka Streams' .split(), or a single join dropping to raw Topology#to() calls for
a manual side output.
**Decision:** Two separate functions (nearDestinationDetection, arrivedDetection),
each with its own join and state store. Thresholds locked at 500m (near) and 50m
(arrival).
**Rationale:** See `docs/decisions/0011-fr12-geofence-thresholds-and-function-split.md`
for full reasoning -- the multi-output array approach was judged too risky to verify
without a broker on hand, given two real binder/YAML config bugs already surfaced
getting FR11's simpler single-output topology working correctly.

---
