# FleetFlow — Delivery & Logistics Tracking Platform

## What this project is

A university group project (CSCI318) building an AI-powered, cloud-native delivery and logistics tracking platform as a microservices system. **This is graded coursework** — architectural correctness and demonstrable patterns matter more than feature count or clever optimisation. The final deliverable is a working repository **and** a written report that must evidence specific patterns.

Team: Tenzin, Steven, Landon. Timeline: weeks 4–12, currently week 6.

## Hard constraints — do not violate

- **Java 21.** Spring Boot.
- **Mandated dependencies only:** Spring Web, Spring Data JPA, H2, Spring Cloud Stream, LangChain4j, Apache Kafka. Plus **Testcontainers** (approved).
- **Do not add any other dependency without asking first.** Additional dependencies require explicit coordinator approval. If you think something is needed, stop and raise it — do not just add it to the POM.
- **No external routing/maps API.** ETA uses Haversine distance × configured average speed per vehicle type. This is a deliberate scope decision.
- **Database per service.** No shared tables, no cross-service foreign keys. Cross-context references are IDs only.
- **No frontend required.** REST endpoints are the interface. A lightweight CLI script or Postman collection for demos is in scope.

## Architecture

Five bounded contexts, each a separate Spring Boot service:

| Service | Sub-domain type | Responsibility |
|---|---|---|
| **Order** | Supporting | Order lifecycle, cancellation |
| **Fleet** | Supporting | Courier profiles, availability, capacity, current location |
| **Dispatch** | **Core** | Courier assignment (agentic + rule-based), reassignment |
| **Tracking** | **Core** | Location stream processing, ETA, geofencing, risk detection |
| **Notification** | Generic | Templated alerts, conversational status explainer |

Plus a **GPS Simulator** — a standalone client that posts location pings to Fleet's REST endpoint. It is *not* a service and has *no* direct Kafka access.

### Locked design decisions

These were debated and settled. Do not revisit them without asking.

1. **Location flow:** Simulator → Fleet REST endpoint → Fleet publishes `CourierLocationUpdated`. Location is a Fleet-context concern; the simulator is an external client.
2. **Dispatch → Fleet coupling:** Dispatch maintains a **local read model** (`courier_availability_view`) updated by consuming Fleet events. Dispatch **never** calls Fleet synchronously. Store latest-position-per-courier only — not ping history, or the table grows unbounded.
3. **Agent vs rules precedence:** The dispatch agent (FR8) is the **primary** path. On **5-second timeout** or error, fall back to rule-based assignment (FR7). Record which path was used in `assignment_decisions`.
4. **Reassignment loop guard:** Maximum **one** automated reassignment per delivery, then a **10-minute cooldown**, then escalate to an operator queue. This prevents `DeliveryAtRisk` → reassign → still-at-risk → reassign cycles.

## Package structure

Every service uses the same four-layer structure. Keep this uniform — the report presents one package tree for all five.

```
com.fleetflow.<service>
├── api/            → presentation layer: REST controllers, DTOs
├── application/    → service layer: use-case orchestration, transactions
├── domain/         → domain layer: entities, value objects, aggregates, domain events, domain services
└── infrastructure/ → infrastructure layer: JPA repositories, Kafka producers/consumers, LLM clients
```

**Layer rules:**
- `domain/` has no Spring or JPA annotations leaking business logic — keep it as close to plain Java as practical
- `api/` never touches repositories directly; it goes through `application/`
- Kafka producers/consumers live in `infrastructure/`, not scattered through services

## Event catalogue and naming

Convention: `<Aggregate><PastTenseVerb>`. Topics: `fleetflow.<context>.<event-kebab-case>`.

Every event is published by the service that owns the aggregate it belongs to.

| Owner | Events |
|---|---|
| Order | `OrderPlaced`, `OrderCancelled` |
| Fleet | `CourierRegistered`, `CourierAvailabilityChanged`, `CourierLocationUpdated` |
| Dispatch | `OrderAssigned`, `OrderReassigned`, `AssignmentFailed` |
| Tracking | `DeliveryStarted`, `DeliveryEtaUpdated`, `DeliveryNearDestination`, `DeliveryArrived`, `DeliveryAtRisk`, `DeliveryCompleted` |
| Notification | `NotificationSent` |

**Do not invent new event names** without adding them here first.

This table lists publishers only. **Before assuming a service consumes an event, check the publisher/consumer matrix in §6.1 of `requirements-outline.md`** — it marks each consumer edge as documented or inferred, and inferred edges need confirmation before you build against them.

## Data ownership

| Service | Tables |
|---|---|
| Order | `orders`, `order_items`, `order_status_history` |
| Fleet | `couriers`, `vehicles`, `availability_windows`, `courier_locations` |
| Dispatch | `assignments`, `assignment_decisions`, `courier_availability_view` (derived) |
| Tracking | `deliveries`, `delivery_track_points` (derived), `eta_snapshots`, `delivery_events` |
| Notification | `notifications`, `notification_templates`, `agent_conversations` |

Distinguish **source-of-truth** data (Fleet's `courier_locations`) from **derived read models** (Dispatch's view, Tracking's track points). This distinction is explicitly assessed.

## DDD patterns — these are graded, be explicit

The report must justify why each domain class belongs to a specific pattern. Write code that makes the classification obvious:

- **Entities** (identity persists through change): `Order`, `Courier`, `Delivery`, `Assignment`
- **Value Objects** (immutable, defined by attributes): `Address`, `GeoCoordinate`, `TimeWindow`, `PackageDimensions`, `Money` — make these genuinely immutable (final fields, no setters, value-based equals/hashCode). Java records are a good fit.
- **Aggregates**: `Order` (root) + items + delivery window; `Courier` (root) + vehicle + availability; `Delivery` (root) + track points. Enforce invariants at the root; do not let external code mutate children directly.
- **Domain Events**: see catalogue above
- **Domain Services** (logic spanning multiple aggregates): `DispatchAssignmentService`, `EtaCalculationService`, `GeofenceEvaluationService`

**When you make a non-obvious DDD decision, leave a short comment explaining the reasoning.** These comments feed directly into the report.

## Agentic AI components

Two agents, deliberately **structurally different** — this contrast is the point:

1. **Dispatch agent** (FR8) — event-triggered decision-maker. Fires on `OrderPlaced`. ReAct-style reason-then-act with tool calls to query fleet state and order backlog. Returns an assignment plus written justification, persisted to `assignment_decisions`.
2. **Status explainer agent** (FR16) — conversational request-response. Answers free-text customer queries via REST. Tool calls to Order and Tracking REST endpoints for order state, ETA and risk flags.

Use LangChain4j `AiServices` with `@Tool`-annotated methods. **Constrain tool access** — agents get read-only queries, and agent output is validated before the system acts on it.

LLM model selection is **still open** — write the integration behind an interface so the model can be swapped. Read the API key from environment/config, never hardcode it.

## Stream processing (Tracking service)

Kafka Streams topologies consuming `CourierLocationUpdated`:

- **ETA recalculation** — stream-table join against active deliveries, recompute arrival, publish `DeliveryEtaUpdated` on material change
- **Geofence detection** — filter on computed distance below threshold, publish `DeliveryNearDestination` / `DeliveryArrived`
- **Anomaly detection** — windowed aggregation per courier, flag stationary/off-pace/SLA-risk deliveries, publish `DeliveryAtRisk`

Keep topologies expressible as SQL-like queries for the report.

## Testing

- **JUnit 5 + Mockito** for domain and service unit tests
- **Testcontainers** for integration tests against a real Kafka broker — do not mock the broker away, the integration behaviour is the point
- Every service slice gets tests as it is built, not deferred to a testing phase

## Documentation obligations — do not skip

The report requires evidence captured *as work happens*:

- **`/docs/ai-collaboration-log.md`** — humans write the decision and rationale. You may capture raw material (what was prompted, what was generated), but **do not write the accept/reject/modify rationale** — that judgment must come from the team member.
- **`/docs/decisions/`** — architecture decision records for non-obvious calls
- **`/docs/prompts/`** — verbatim prompt (and response) text worth quoting directly in the report, not every prompt. "Notable" is too vague to act on by itself, so save one here **at the moment you'd want to quote it in the report**, using any of these three triggers rather than waiting to judge it later:
  - the prompt behind an **accepted ADR** in `docs/decisions/`
  - a prompt where the AI's first answer was **rejected or modified**, not accepted outright — the log says "Modified," and that back-and-forth is the most interesting Section 5 evidence there is
  - a prompt demonstrating a **deliberate prompting technique** worth explaining (constrained tool access, structured output, multi-turn refinement of an agent's behaviour)

  Use the template in `docs/prompts/0000-template.md`, numbered like the ADRs. Waiting until Week 11 to reconstruct which raw prompt underpinned which decision does not work — capture it the session it happens.
- Keep the README current with setup and run instructions

**End-of-branch habit:** when wrapping up work on a branch, if the session produced a notable AI-assisted decision worth Section 5 evidence, draft the corresponding `ai-collaboration-log.md` entry (or entries) in chat for a team member to review and paste in — never write directly into the log. If the decision already has an ADR in `docs/decisions/`, keep the log entry to a short pointer (prompt/context, which ADR, one-line decision) rather than re-stating the ADR's full reasoning — the log is breadth evidence across many small interactions, the ADR is depth evidence for the few calls substantial enough to need one; don't duplicate the same paragraphs into both. Skip this step entirely when nothing in the session was a real AI-assisted decision worth logging — not every branch needs an entry.

## Working style

- **Ask before adding dependencies, changing locked decisions, or inventing event names.**
- Prefer clarity over cleverness — this code is read by markers, not just run.
- When a task spans multiple services, confirm which slice is being worked on. Ownership: Tenzin (Order, Fleet, Notification wiring, Docker Compose), Steven (Dispatch, GPS simulator, dispatch agent), Landon (Tracking, stream processing, explainer agent).
- Small, reviewable commits. This is a shared repo with three people.
- **Never commit directly to `main`.** Every change, docs included, is made on a branch: fast-forward local `main` first, branch off it, do the work, then open a PR (or hand off for merge) rather than pushing straight to `main`.

## Reference

Full requirements outline (16 functional requirements, 8 use cases, business objectives) lives at `/docs/requirements-outline.md`. Read it before starting substantive work.
