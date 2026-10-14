# Delivery & Logistics Tracking Platform — Requirements Outline

**Application name:** *FleetFlow* — locked, no longer a placeholder (§12 closed item, see "Closed since v2")
**Team:** Tenzin, Steven, Landon
**Status:** Draft v2 — for pre-implementation sign-off
**Revision note:** v2 adds business objectives, use case grouping, user stories, sub-domain classification, and locks four design decisions previously left open.

---

## 1. Business Objectives

These are the objectives every functional requirement traces back to. Referenced throughout as **O1–O5**.

| ID | Objective | Stakeholder |
|----|-----------|-------------|
| **O1** | Provide continuous, accurate visibility of delivery progress | Customer, Operations |
| **O2** | Maximise courier utilisation and minimise inefficient assignment | Operations, Business owner |
| **O3** | Detect and resolve delivery exceptions before they become failures | Operations |
| **O4** | Reduce manual customer-support load through self-service and proactive comms | Support team, Customer |
| **O5** | Maintain an auditable record of every delivery for dispute resolution | Business owner, Customer |

---

## 2. Sub-domains and Bounded Contexts

Sub-domains describe the *problem space*; bounded contexts are the *solution-space* boundaries we implement as services.

| Sub-domain | Classification | Bounded Context / Service | Justification |
|---|---|---|---|
| Dispatch & Routing | **Core** | Dispatch | Agentic assignment optimisation is the system's differentiator |
| Delivery Tracking | **Core** | Tracking | Real-time ETA, geofencing and risk detection deliver O1 and O3 |
| Order Management | **Supporting** | Order | Essential but non-differentiating; every delivery business has it |
| Fleet Management | **Supporting** | Fleet/Courier | Industry-generic roster and availability management |
| Customer Notification | **Generic** | Notification | Message dispatch is a commodity capability |

**Acknowledged tension:** FR16 (the agentic status explainer) sits within Notification, classified as generic — an AI differentiator inside a commodity sub-domain. We keep it there deliberately rather than introducing a sixth service, and the report will state this as an intentional simplification. Engineering effort is otherwise concentrated in the two core sub-domains, as DDD prescribes.

---

## 3. Use Cases

Sixteen functional requirements grouped into **eight use cases** — each use case gets one UML activity diagram (Section 2.1 of the report), rather than sixteen.

| UC | Use Case | FRs | Primary Actor |
|----|----------|-----|---------------|
| UC1 | Manage Delivery Order | FR1, FR2, FR3 | Customer |
| UC2 | Manage Fleet and Courier Profiles | FR4, FR5, FR6 | Operator, Courier |
| UC3 | Assign Courier to Order | FR7, FR8 | System / Dispatch Agent |
| UC4 | Handle Delivery Exception | FR9 | System |
| UC5 | Capture Courier Location | FR10 | Courier (simulated) |
| UC6 | Monitor Delivery in Real Time | FR11, FR12, FR13 | System, Operator |
| UC7 | Complete Delivery and Record Proof | FR14 | Courier, Customer |
| UC8 | Notify and Explain Delivery Status | FR15, FR16 | Customer |

---

## 4. Functional Requirements

Tags: **[STREAM]** = real-time stream processing · **[AGENT]** = agentic AI

### UC1 — Manage Delivery Order *(Order Service)*

**FR1 — Create delivery order** → *O1, O5*
Customer submits pickup address, drop-off address, package details and service level. System validates, persists, publishes `OrderPlaced`.

**FR2 — Query order status** → *O1, O4*
Retrieve current state (Placed → Assigned → In Transit → Delivered / Failed) with timestamps.

**FR3 — Cancel order** → *O2*
Cancel a pre-dispatch order; releases any held assignment, publishes `OrderCancelled`.

### UC2 — Manage Fleet and Courier Profiles *(Fleet Service)*

**FR4 — Register and manage couriers** → *O2*
Operator registers couriers with vehicle type, capacity and operating zone; supports update and deactivation. Publishes `CourierRegistered`.

**FR5 — Manage courier availability** → *O2*
Couriers set on/off shift; publishes `CourierAvailabilityChanged`.

**FR6 — Track courier load and capacity** → *O2*
Maintains active-order count against capacity ceiling to prevent overloading.

### UC3 — Assign Courier to Order *(Dispatch Service)*

**FR7 — Rule-based courier assignment (fallback path)** → *O2*
Deterministic selection by proximity, capacity and zone. Publishes `OrderAssigned`.

**FR8 — Agentic dispatch optimisation (primary path)** **[AGENT]** → *O2, O3*

> **User story:** *As an operations manager, I want the system to intelligently assign couriers by weighing proximity, current load and SLA pressure across all open orders, so that I achieve better fleet utilisation than fixed rules allow without manual intervention.*

An LLM-backed agent reasons over open orders, courier positions, capacity and SLA pressure to recommend an assignment with a written justification. Uses tool calls to query fleet state and order backlog before deciding.

**Precedence (locked):** The agent is the **primary** path. On timeout (**5 seconds**) or error, the system falls back to FR7's rule-based assignment and records which path was used in `assignment_decisions`. This fallback is a deliberate resilience decision, not an afterthought — see §9.7.

### UC4 — Handle Delivery Exception *(Dispatch Service)*

**FR9 — Reassign on exception** → *O3*
On courier going offline mid-delivery or a predicted SLA breach, trigger reassignment and publish `OrderReassigned`.

**Loop guard (locked):** Maximum **one automated reassignment per delivery**, plus a **10-minute cooldown** before any further automated action. Beyond that, the delivery is escalated to an operator queue rather than reassigned again. This prevents the `DeliveryAtRisk` → reassign → still-at-risk → reassign cycle.

### UC5 — Capture Courier Location *(Fleet Service)*

**FR10 — Ingest courier location updates** → *O1*
GPS Simulator posts pings to a **Fleet REST endpoint**; Fleet validates, updates the courier's current position and publishes `CourierLocationUpdated` to Kafka.

**Decision (locked):** Location is a Fleet-context concern. The simulator is an external client, not an event producer — it has no direct Kafka access. This keeps every event published by the service that owns the aggregate it belongs to.

**Simulator fidelity (locked):** Random-walk movement, not route-based. Settled in the Week 5 team discussion that §12 previously carried as "leaning random-walk" pending formal close-out.

### UC6 — Monitor Delivery in Real Time *(Tracking Service)*

**FR11 — Real-time ETA recalculation** **[STREAM]** → *O1*

> **User story:** *As a customer awaiting a delivery, I want an ETA that updates continuously as my courier moves, so that I can plan around the arrival rather than relying on a stale estimate made at dispatch time.*

Kafka Streams topology consumes `CourierLocationUpdated`, keys by active delivery, and recomputes estimated arrival. Material changes publish `DeliveryEtaUpdated`.

**Calculation method (locked):** Travel time = Haversine great-circle distance / configured average speed per vehicle type; ETA = GPS capture time + travel time. **No external routing API.** Documented in the report as a deliberate scope decision — road-network routing adds an external dependency and network latency inside a stream topology for no additional marks.

**FR12 — Geofence arrival detection** **[STREAM]** → *O1, O4*

> **User story:** *As a customer, I want to be alerted when my courier is minutes away, so that I can be ready to receive the delivery.*

Stream processor evaluates each ping against the destination geofence radius; publishes `DeliveryNearDestination` then `DeliveryArrived`.

**FR13 — SLA breach and anomaly detection** **[STREAM]** → *O3*
Windowed aggregation flags deliveries stationary beyond a threshold, deviating from expected progress, or trending toward a promised-time breach. Publishes `DeliveryAtRisk`.

UC6 implementation, configurable thresholds and review status are recorded in [ADR 0013](decisions/0013-uc6-windowed-risk-and-delivery-snapshots.md).

### UC7 — Complete Delivery and Record Proof *(Tracking Service)*

**FR14 — Delivery history and proof of delivery** → *O5*
Persist completed delivery record — route summary, timestamps, confirmation. Exposed via REST. Publishes `DeliveryCompleted`.

### UC8 — Notify and Explain Delivery Status *(Notification Service)*

**FR15 — Event-driven customer notifications** → *O1, O4*
Subscribes to delivery-lifecycle events; dispatches templated notifications (assigned, reassigned, assignment failed, in transit, near destination, delivered, at risk).

**FR16 — Agentic delivery status explainer** **[AGENT]** → *O4*

> **User story:** *As a customer with a delayed delivery, I want to ask in plain language why my order is late and get a real explanation, so that I don't have to contact support or decode a status code.*

Conversational agent answers free-text queries. Uses tool calls to fetch order state (Order REST), current ETA and risk flags (Tracking REST), then produces a natural-language explanation.

---

## 5. Requirement Coverage Check

| Spec requirement | Minimum | Provided | Covered by |
|---|---|---|---|
| Functional requirements | 12 | **16** | FR1–FR16 |
| Real-time streaming user stories | 2 | **3** | FR11, FR12, FR13 |
| Agentic AI user stories | 2 | **2** | FR8, FR16 |
| Bounded contexts / microservices | 4 | **5** | Order, Fleet, Dispatch, Tracking, Notification |
| Use cases with activity diagrams | — | **8** | UC1–UC8 |

FR13 is deliberate slack — if windowed anomaly detection proves too fiddly late in the build, dropping it still leaves you compliant on the streaming minimum.

---

## 6. Event Catalogue

Every event is published by the service owning the aggregate it belongs to. **Lock this before Week 6 work begins.**

| Owning Service | Events Published |
|---|---|
| **Order** | `OrderPlaced`, `OrderCancelled` |
| **Fleet** | `CourierRegistered`, `CourierAvailabilityChanged`, `CourierLocationUpdated`, `CourierLoadChanged` |
| **Dispatch** | `OrderAssigned`, `OrderReassigned`, `AssignmentFailed` |
| **Tracking** | `DeliveryStarted`, `DeliveryEtaUpdated`, `DeliveryNearDestination`, `DeliveryArrived`, `DeliveryAtRisk`, `DeliveryCompleted` |
| **Notification** | `NotificationSent` |

Naming convention: `<Aggregate><PastTenseVerb>`. Topic naming: `fleetflow.<context>.<event>` (e.g. `fleetflow.fleet.courier-location-updated`).

### 6.1 Publisher/Consumer Matrix

§6 above documents publishers only. That one-sidedness is exactly how a non-existent `Order → Fleet` edge once made it into a work instruction unchallenged — with no consumer column, nothing contradicted it. This table adds the other half, built by tracing every FR rather than assumed.

Each cell is marked **documented** (stated in this outline or `AGENTS.md`) or **inferred** (implied by an FR but never stated anywhere). Inferred cells are not settled — confirm each one with the owning service pair before building against it, not after.

| Event | Publisher | Consumers |
|---|---|---|
| `OrderPlaced` | Order | Dispatch *(documented — `AGENTS.md` line 104)*, Tracking *(documented — FR11, delivery-destination bootstrap)* |
| `OrderCancelled` | Order | Dispatch *(documented — backlog removal and held-assignment cancellation, FR3 review fix)*, Fleet *(documented — releases active load and retains a cancellation tombstone for late assignment events)* |
| `CourierRegistered` | Fleet | Dispatch *(documented — §7)*, Tracking *(documented — FR11, vehicle/average-speed bootstrap)* |
| `CourierAvailabilityChanged` | Fleet | Dispatch *(documented — §7; also the FR9 offline trigger)* |
| `CourierLocationUpdated` | Fleet | Dispatch *(documented — §7)*, Tracking *(documented — FR11–FR13)* |
| `CourierLoadChanged` | Fleet | Dispatch *(documented — ADR 0009; UC3 now projects versioned load and acknowledges local reservations)* |
| `OrderAssigned` | Dispatch | Tracking *(documented — delivery bootstrap)*, Notification *(documented — FR15)*, Fleet *(documented — accepted ADR 0002, FR6 active-order count)*, Order *(documented — FR2 status: ASSIGNED)* |
| `OrderReassigned` | Dispatch | Tracking, Fleet *(implemented in UC4, ADR 0017)*; Notification *(documented — FR15, implemented)* |
| `AssignmentFailed` | Dispatch | Notification *(documented — FR15 review fix)*, Order *(documented — FR2 status: FAILED)* |
| `DeliveryStarted` | Tracking | Dispatch *(UC4 active delivery projection, ADR 0017)*, Order *(documented — FR2 status: IN_TRANSIT)*, Notification *(documented — FR15, "in transit")* |
| `DeliveryEtaUpdated` | Tracking | Notification *(inferred)* |
| `DeliveryNearDestination` | Tracking | Notification *(documented — FR15)* |
| `DeliveryArrived` | Tracking | Dispatch *(UC4 terminal projection, ADR 0017)*, Tracking *(internal arrival persistence/snapshot cleanup — ADR 0013)*, Notification *(inferred)* |
| `DeliveryAtRisk` | Tracking | Dispatch *(documented — FR9)*, Notification *(documented — FR15)* |
| `DeliveryCompleted` | Tracking | Dispatch *(UC4 terminal projection, ADR 0017)*, Order *(documented — FR2 status: DELIVERED)*, Notification *(documented — FR15)*, Fleet *(documented — accepted ADR 0002, FR6 active-order count)* |
| `NotificationSent` | Notification | none — terminal, audit-only. Worth confirming this is wanted at all rather than assumed. |

Two schema gaps surfaced directly by building this matrix, both raised as ADRs rather than resolved here:

- **Dispatch has no open-orders backlog to reason over for FR8**, despite consuming `OrderPlaced` — see `docs/decisions/0001-dispatch-open-orders-backlog.md` (Dispatch service owner). Resolved: `open_orders_view` is built (§8), consuming `OrderPlaced`/`OrderCancelled`; `OrderAssigned` removal is deferred to FR7/FR8.
- **Fleet stores the FR6 active-order count on `couriers`**, consuming `OrderAssigned` / `DeliveryCompleted` with transactional event and order ledgers — see `docs/decisions/0002-fleet-active-order-count.md` (Fleet service owner).

---

## 7. Inter-Service Communication (Locked)

**Dispatch → Fleet: local read model, not synchronous REST.**
Dispatch maintains its own `courier_availability_view`, updated by consuming `CourierRegistered`, `CourierAvailabilityChanged`, `CourierLocationUpdated` and `CourierLoadChanged`. Dispatch never calls Fleet synchronously.

*Rationale for the report:* avoids distributed coupling between bounded contexts — Dispatch continues assigning even if Fleet is unavailable. The trade-off is eventual consistency: the read model may lag by the event propagation delay, acceptable because assignment tolerates a courier position that is seconds stale.

*Implementation note:* `CourierLocationUpdated` is high-frequency. Dispatch's read model should store **latest position per courier only** (compacted view), not a ping history — otherwise the table grows unbounded.

**REST is still used across services**, satisfying the spec's REST requirement:
- Client → Order, Tracking, Notification (all customer-facing endpoints)
- GPS Simulator → Fleet (location ingestion, FR10)
- Notification's explainer agent → Order and Tracking (tool calls, FR16)

---

## 8. Data Ownership

Database-per-service. No shared tables. Cross-context references held as IDs only — never foreign keys into another service's schema.

| Service | Owns |
|---|---|
| **Order** | `orders`, `order_items`, `order_status_history`, `processed_events`¹ |
| **Fleet** | `couriers`, `vehicles`, `availability_windows`, `courier_locations` (current position), `courier_order_load` (FR6 event-order ledger), `processed_events`¹ |
| **Dispatch** | `assignments`, `assignment_decisions` (incl. agent rationale and path used), `courier_availability_view` (derived read model), `open_orders_view` (derived read model, ADR 0001), `order_cancellation_state` (serializes placement/cancellation and retains tombstones), `processed_events`¹ |
| **Tracking** | `deliveries`, `delivery_track_points` (delivery-scoped, derived from stream), `eta_snapshots`, `delivery_events`, `processed_events`¹ |
| **Notification** | `notifications`, `notification_templates`, `agent_conversations`, `processed_events`¹ |

¹ `processed_events` — the idempotency ledger, keyed on `EventEnvelope.eventId()` (see `fleetflow-contracts`). An **infrastructure concern, not a domain entity** — it belongs in `infrastructure/`, never `domain/`, and does not appear in the DDD pattern classification in §9.3. Added here because §8 is a report deliverable compared against the real schema, and a table present in five databases but absent from this table reads as an omission. Every service in this row is a genuine event consumer per the §6.1 matrix, so every service needs one — confirm the exact consuming event set per service as its slice is built, per §6.1.

Note the deliberate distinction between **owned source-of-truth data** (Fleet's `courier_locations`) and **derived read models** (Dispatch's `courier_availability_view`, Tracking's `delivery_track_points`) — worth calling out explicitly in the report as evidence of correct bounded-context isolation.

---

## 9. Report Section Coverage

### 9.1 Microservice Architecture
Diagram shows five services plus the GPS Simulator, with REST flows (§7) and Kafka event flows (§6) distinguished visually. Data flows traceable end to end: order placement → assignment → location stream → ETA → notification.

### 9.2 Layered Architecture
Uniform four-layer package structure across all services:

```
com.fleetflow.<service>
├── api/            → presentation layer (REST controllers, DTOs)
├── application/    → service layer (use-case orchestration, transactions)
├── domain/         → domain layer (entities, value objects, aggregates, domain events, domain services)
└── infrastructure/ → infrastructure layer (JPA repositories, Kafka producers/consumers, LLM clients)
```

One package tree and one explanation covers all five services; note per-service variations briefly.

### 9.3 Domain-Driven Design Patterns

| Pattern | Examples |
|---|---|
| **Entity** | `Order`, `Courier`, `Delivery`, `Assignment` — identity persists through state change |
| **Value Object** | `Address`, `GeoCoordinate`, `TimeWindow`, `PackageDimensions`, `Money` — immutable, defined by attributes |
| **Aggregate** | `Order` (root) + order items + delivery window; `Courier` (root) + vehicle + availability; `Delivery` (root) + track points |
| **Domain Event** | See §6 catalogue |
| **Domain Service** | `DispatchAssignmentService`, `EtaCalculationService`, `GeofenceEvaluationService` |

**Write these justifications during implementation, not in Week 9.** Each needs a DDD-principle rationale plus a code snippet.

### 9.4 Data Persistence
Per §8. Include an ER-style diagram per service plus the ownership table.

### 9.5 Event-Driven Architecture and Stream Processing
- Spring Cloud Stream bindings over Kafka; events published from the owning aggregate's service
- Stream queries to document in SQL-like form: ETA recalculation (stream-table join), geofence detection (filter on computed distance), anomaly detection (windowed aggregation per courier)
- Sample code: Kafka Streams topology, `@Bean Function<KStream<...>, KStream<...>>` binding, producer snippet

### 9.6 Agentic AI Component
- **Patterns:** tool use / function calling (both agents), ReAct-style reason-then-act (dispatch agent), event-triggered invocation (FR8) vs. conversational request-response (FR16)
- **Integration:** LangChain4j `AiServices` with `@Tool`-annotated methods
- **Model (locked):** Google Gemini `gemini-2.0-flash`, via the free Google AI Studio API, for both FR8 and FR16. See `docs/decisions/0005-llm-model-selection.md` for the full option comparison and rationale.
- **Sample code:** agent interface, tool definition, invoking service, captured input/output pair

The two agents are **structurally different by design** — an event-triggered decision-maker versus a conversational assistant. This is the differentiator in Section 3.6.

### 9.7 Other Important Aspects
Each item below is **assigned to a work slice** — nothing claimed that nobody is building.

| Aspect | Owner | Slice |
|---|---|---|
| Rule-based dispatch fallback + 5s agent timeout | Steven | FR7/FR8 |
| Reassignment loop guard (1 retry + cooldown) | Steven | FR9 |
| Idempotent Kafka consumers | All | Own service's consumers |
| Agent guardrails (constrained tool access, output validation) | Steven, Landon | FR8, FR16 |
| Testcontainers integration tests against real broker | All | Own slice |
| Docker Compose single-command local environment | Tenzin | Infrastructure |

---

## 10. AI-Driven Development Process (Report Section 5)

**This has no owner in the current WBS and cannot be reconstructed retrospectively. Start in Week 5.**

Repository structure:

```
/docs
  ├── ai-collaboration-log.md      ← running chronological log
  ├── prompts/                     ← notable prompts worth citing in the report
  └── decisions/                   ← architecture decision records
```

**Written by humans, not the coding agent.** The agent may capture raw material (what was prompted, what was generated); the *decision and rationale* must be written by the team member — that judgment is where Section 5's marks are.

Entry format:

```
## 2026-09-08 — Dispatch aggregate boundary (Steven)
**Prompt/context:** Asked assistant to model Assignment as part of the Order aggregate.
**Proposed:** Assignment as a child entity under Order.
**Decision:** Rejected.
**Rationale:** Assignment belongs to the Dispatch context; nesting it under Order
would have leaked dispatch logic across a bounded-context boundary.
```

Target one entry per person per working session (~15–25 by Week 11). Make it part of each person's **definition of done** for their own slice rather than a shared task that gets deferred.

Also required for the cover page: a **member contribution/accountability log**, maintained from now.

---

## 11. Dependency Status

| Dependency | Status |
|---|---|
| Spring Web, Spring Data JPA, H2, Spring Cloud Stream, LangChain4j | Mandated by spec |
| Apache Kafka | Mandated by spec |
| **Testcontainers** | **Approved by coordinator** |
| Kafka Streams binder | Confirm with coordinator (distinct from base Spring Cloud Stream) |
| Docker / Docker Compose | Confirm with coordinator |
| Postman / Newman | Confirm with coordinator |

---

## 12. Remaining Open Decisions

1. **Geofence radius and SLA thresholds** — pick defaults now, tune during testing

### Closed since v2

- **LLM model selection** — closed. Google Gemini `gemini-2.0-flash`, via the free
  Google AI Studio API, for both FR8 and FR16. See
  `docs/decisions/0005-llm-model-selection.md` and §9.6.
- **GPS simulator fidelity** — closed. Random-walk, per the Week 5 team discussion. See
  UC5.
- **Application name** — closed. *FleetFlow*, no longer a placeholder. See the title
  block above.
