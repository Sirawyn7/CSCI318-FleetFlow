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
`docs/decisions/0012-dispatch-capacity-reservations-and-zones.md`.
**Decision:** Accepted the UC3 fixes described in prompt 0009, while keeping
option 3 from ADR 0012. The agent remains the main dispatch path, with FR7 as the
fallback. Both paths still have to follow the same zone, capacity, and reservation
checks.
**Rationale:** I wanted to keep the agent-based approach, but waiting for the
agent should not block other assignments or courier updates. Checking the courier
again before saving an assignment helps prevent using capacity that is no longer
available. The load-event and zone-boundary fixes also make the existing checks
more reliable. These changes improve the implementation without changing the
overall approach I accepted in
`docs/decisions/0012-dispatch-capacity-reservations-and-zones.md`.

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

## 2026-10-05 — FR14 delivery completion and proof (Tenzin)
**Prompt/context:** Asked the assistant to implement FR14 in Tracking Service,
covering delivery completion, proof, REST access and `DeliveryCompleted` publication.
**Proposed by agent:** Store a courier-supplied route summary, confirmation and
completion timestamp on Delivery, with immutable proof and retry protection.
**Decision:** Accepted option 1, as recorded in
`docs/decisions/0014-FR14-completion-proof.md`.
**Rationale:** See `docs/decisions/0014-FR14-completion-proof.md` for the reasoning
and limitations of using textual proof instead of GPS-derived route history.
**Prompt record:** `docs/prompts/0013-uc7-fr14-completion-proof.md`.
**Authorship:** Entry drafted with AI assistance at Tenzin's request, summarising
the existing accepted ADR.

## 2026-09-29 — FR13 risk detection and UC6 review (Tenzin)
**Prompt/context:** Asked the assistant to complete FR13 and review the existing
FR11/FR12 implementation. See `docs/prompts/0010-uc6-completion-and-review.md`.
**Proposed by agent:** Add windowed stationary, off-pace and SLA-risk detection,
with configurable thresholds and repeated-alert suppression. Also correct GPS-time
ETA calculation, support multiple deliveries per courier, and improve bootstrap
retry and snapshot recovery. See
`docs/decisions/0013-uc6-windowed-risk-and-delivery-snapshots.md` for the design
options and implementation details. Verification passed 42 unit/regression tests
and 3 real-Kafka integration tests.
**Decision:** Accepted
**Rationale:** This adds risk detection without changing the existing ETA and geofence setup. I also changed the tracking so a courier can have multiple deliveries instead of just one. The observation window and cooldown are there to avoid sending alerts too early or repeatedly. The thresholds can still be adjusted later if needed after more testing. ADR 0013 covers the other options considered and the current limitations.
---

## 2026-10-08 — ADR 0013 review follow-up (Tenzin)
**Prompt/context:** Addressed the PR review of UC6 and the conflicting ADR review status. See `docs/prompts/0011-uc6-review-corrections.md`.
**Decision:** Accepted option 1, as explicitly confirmed by Tenzin in this session.
**Rationale:** See the Decision section of `docs/decisions/0013-uc6-windowed-risk-and-delivery-snapshots.md` for Tenzin’s explanation.
**AI assistance:** The assistant proposed draft wording and suggested spelling and terminology corrections; Tenzin supplied the final acceptance and revised paragraph. This records the confirmation on 2026-10-08 and does not establish when the earlier ADR review occurred.

---

## 2026-10-11 — Missing notifications and cancellation release review (Tenzin)
**Prompt/context:** Fix missing assignment notifications and cancellation not releasing capacity.
**Proposed by agent:** Add `OrderReassigned` and `AssignmentFailed` notifications;
release Dispatch reservations and Fleet load on cancellation, handling duplicate and out-of-order events.
**Validation:** Re-review verified `mvn clean install` and 222 non-integration tests,
including schema upgrade coverage. Reported Docker results on 2026-10-12: Notification
14/14, Fleet cancellation Kafka 2/2, Dispatch cancellation Kafka 2/2, and Order's
separate opt-in Kafka test passed with zero skips. Full reactor `mvn install` also
passed, including the remaining Kafka suites. The reviewer could not rerun Kafka tests without Docker.
**Follow-ups:** FR9 publication, Order status updates and Tracking cancellation.
**Decision:** Accepted.
**Rationale:** These changes fix both issues raised in the review. Customers can now receive notifications when an assignment fails or a delivery is reassigned. Cancelled orders also release the courier's capacity. Duplicate and out-of-order events are handled to keep load counts accurate.
**Authorship:** Tenzin made the decision and supplied the final revised rationale.
The assistant provided the initial draft and edited the supporting sections.

---

## 2026-10-12 — FR9 / UC4 review follow-up (Tenzin)
**Prompt/context:** Review requested a durable attempt claim before agent invocation,
selection of due escalation rows, preservation of no-candidate reasons and README cleanup.
**AI changes:** Implemented the review corrections and added regression coverage. See
[ADR 0017](decisions/0017-uc4-durable-reassignment-guard.md) for implementation details.
**Prompt record:** [Review follow-up excerpt](prompts/0015-uc4-review-corrections.md).
The original implementation prompt behind ADR 0017 is unavailable; the linked record covers the review follow-up.
**Validation:** Full reactor non-integration suite passed (234 tests); the real-Kafka
`DeliveryExceptionIntegrationTest` passed (1 test) using Docker Desktop on 2026-10-12.
**Decision:** Store the reassignment guard and pending event on `Assignment`.
**Rationale:** Keeping both in `Assignment` ensures the attempt count is saved even if the service restarts. Claiming the attempt under a row lock prevents duplicate reassignment attempts, while failed publications can be retried without adding extra workflow or outbox tables. This keeps the design simple, as each delivery only gets one automated reassignment attempt.
