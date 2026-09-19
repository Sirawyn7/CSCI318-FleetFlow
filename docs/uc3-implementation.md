# UC3 — Assign Courier to Order

## Implemented behaviour

`OrderPlaced` first updates `open_orders_view`, then invokes `AssignmentService`.
The service checks its decision ledger and snapshots the available fleet and open
backlog without a transaction or row locks. Both selection paths require a known courier
location, a configured zone containing the pickup, and at least one remaining slot.

FR8 runs first through the `DispatchAgent` port. `GeminiDispatchAgent` uses
LangChain4j `AiServices` and two `@Tool` methods: `fleetState` and `openOrders`.
The tools expose immutable copies, including load, pending reservations, positions,
service levels and delivery deadlines. They expose no writes or cross-service REST
calls. The adapter rejects answers that did not consult both tools.

The application checks that the returned order is the target order, the courier
is eligible, and a nonblank justification of at most 2,000 characters is present.
The whole model/tool loop is bounded to five seconds, with no provider retries and
at most three sequential tool rounds. Busy workers, provider failures, malformed
output, invalid choices and timeouts trigger FR7. Late agent results cannot write
state or publish events. Interruption while awaiting the agent cancels its work
before the commit transaction is entered.

FR7 selects the nearest eligible courier using Haversine distance, then greatest
remaining capacity, then lowest UUID. Success persists the assignment and decision,
removes the backlog entry and publishes `OrderAssigned`. Failure persists a
`RULE_BASED` / `FAILED` decision and publishes `AssignmentFailed`; the order remains
in the backlog. The decision stores the agent rationale or the fallback/failure
reason. Provider exception messages are not persisted because they may contain
credentials. A duplicate order delivery cannot create a second decision/assignment.

`AssignmentCommitService` rechecks the order and chosen courier in a short separate
transaction, locking only that order and courier. It revalidates current availability,
location, zone and remaining capacity before reserving a slot. A stale agent choice
triggers refreshed rule-based ranking. Each candidate attempt releases its locks
before trying another, so it never accumulates locks across the fleet. Cancellation
or an existing decision prevents a second commit. The external AI call holds no
database locks, allowing other orders and Fleet updates to continue during its wait.

`DeadlineHttpClient` waits on the existing JDK transport's asynchronous response with
a finite deadline and cancellation. Its worker exits on timeout even if cancellation
of the underlying network future is ineffective. Connection and request timeouts
also remain enabled in the transport. This avoids tying the bounded agent workers
to blocking socket I/O; tests cover an unresponsive, non-cancellable response and
subsequent reuse of the same worker.

## Capacity and event ordering

Fleet remains authoritative for active load. It publishes `CourierLoadChanged`
after handling assignments/completions, including semantic duplicates that still
need to acknowledge a Dispatch reservation. The contract adds `loadVersion` and
`acknowledgedOrderId` to the existing event. Fleet increments the version whenever
the active count changes. Dispatch uses that version to ignore older load values,
while still acknowledging the older event's specific order.
Malformed counts/versions are rejected before checking staleness. When a valid
acknowledgement refers to an absent local assignment (historical, seeded or rolled
back), Dispatch logs the order/courier IDs, keeps Fleet's authoritative load and
marks the event processed. There is no local reservation to release. Later load
updates continue normally; this does not reconstruct missing assignment history.

Dispatch's remaining capacity is:

```text
capacity ceiling - Fleet active-order count - unacknowledged Dispatch assignments
```

Saving an assignment reserves a slot. Applying its Fleet acknowledgement updates
load and marks that assignment acknowledged in one transaction. Courier locks
serialize reservation and acknowledgement updates, including across service
instances. Reordered acknowledgements can temporarily understate free capacity,
but cannot expose an already-reserved slot as free. Failed synchronous publication
rolls back local writes and allows retry. Registration redelivery cannot reset an
existing projection. Competing first registrations use an insert-only write and a
single retry in a fresh transaction after a primary-key collision; a missing row
cannot itself be row-locked. Availability/location changes share courier locks and reject
older timestamps. Missing registrations cause retry rather than silently dropping
dependent updates; exhausted retries go to the configured Kafka dead-letter topic.

## Configuration and demo

Dispatch runs on port 8083; Fleet on 8082. Start Kafka and the Order, Fleet and
Dispatch services as described in the root README. Supply `GEMINI_API_KEY` through
the Dispatch process environment. Never put it in committed files.

`GEMINI_MODEL` overrides the model name. The ADR 0005 default remains
`gemini-2.0-flash` pending approval to change a locked decision. Google lists that
model as shut down on June 1, 2026. A live FR8 demo requires agreeing a supported
model, configuring it and testing with your account; do not interpret fallback
success as proof of a live agent call.

Source: [Google Gemini model deprecations](https://ai.google.dev/gemini-api/docs/deprecations).
Implementation API reference: [LangChain4j Gemini integration](https://docs.langchain4j.dev/integrations/language-models/google-ai-gemini/).

The `dispatch.zones` map in `dispatch-service/src/main/resources/application.yml`
contains two illustrative geographic rectangles. `ZONE_A` covers the existing
Wollongong sample pickup (-34.4, 150.8); `ZONE_B` covers a Sydney demo area.
These are configurable examples, not official service boundaries. Preserve the
bracketed map-key notation (`"[ZONE_A]"`) so Spring retains the underscore. Unknown
zone names and pickups outside the configured rectangle fail eligibility. Rectangles
include their minimum coordinates and exclude their maximum coordinates, so the
shared latitude -34.0 belongs to `ZONE_B` alone. Arbitrarily overlapping configured
rectangles can still overlap; configure disjoint areas if zones must partition the fleet.

1. Register a courier in `ZONE_A` with positive capacity.
2. Set availability with `PATCH /api/couriers/{id}/availability` and `{"available":true}`.
3. Send its location with `POST /api/couriers/{id}/location` and
   `{"latitude":-34.41,"longitude":150.81}`.
4. Wait for the courier registration, availability and location to reach Dispatch.
5. Create an order through Order's API with pickup (-34.4, 150.8).
6. Inspect Dispatch's H2 database through its existing `/h2-console`:

```sql
SELECT order_id, courier_id, path_used, outcome, reason, decided_at
FROM assignment_decisions ORDER BY decided_at DESC;

SELECT order_id, courier_id, load_acknowledged FROM assignments;

SELECT courier_id, capacity, active_order_count, load_version
FROM courier_availability_view;
```

A successful live response produces `AGENT`; missing credentials, provider errors
or timeouts produce `RULE_BASED` with an explanation. Replaying the same order
event must not increase the decision/assignment count. A capacity-one courier
cannot receive another order until Fleet reports its completion.

## Verification

Unit, real H2 persistence/concurrency, configuration-binding, and simulated
LangChain4j tool-loop tests (no Docker or live API needed):

```text
mvn -pl dispatch-service,fleet-service -am test "-Dtest=*Test,!*IntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Real Kafka integration tests (Docker required):

```text
mvn -pl dispatch-service,fleet-service -am test
```

The Kafka assignment test uses a deterministic test agent while exercising the real
broker, consumer, database and output topics. Live Gemini quality/latency still
needs a separate credentialed smoke test.

Verification on 2026-09-17: **88 tests passed** (36 Fleet, 52 Dispatch), including
real Kafka integration tests after starting the local Docker engine. The broader
non-Docker reactor check passed Order, Fleet, Dispatch, Tracking and Notification;
GPS Simulator reported an unrelated numeric assertion mismatch (`BigDecimal`
versus the test's `Double` matcher). The GPS files were not changed. No Gemini key
or model override was configured in the execution environment, so live provider
latency/quality was not tested.

Review-fix verification on 2026-09-19: **100 tests passed** (36 Fleet, 64 Dispatch),
including all real Kafka integration tests. New regressions cover other orders and
Fleet updates progressing during a blocked agent call, cancellation and eligibility
changes before commit, orphan acknowledgements, invalid/stale versions, adjacent
zone edges, concurrent registration and bounded asynchronous HTTP waits. The
runtime HTTP transport is also exercised against a local HTTP server. No live
Gemini request was made. `git diff --check` passed.

## Limits and follow-up work

- Rule ranking uses a snapshot; the short commit transaction guarantees eligibility
  and capacity for the chosen courier, not globally optimal ranking under concurrent
  fleet changes. Event publication still occurs inside that short transaction.
- Database commits and Kafka sends are not an atomic distributed transaction.
  The existing synchronous-publish approach detects failed sends, but a process
  crash after broker acceptance and before database commit remains a consistency
  window. An outbox would be a separate resilience change.
- Previously failed orders remain visible in the backlog, but there is no automatic
  retry/sweep when a courier becomes available. UC3 handles one attempt per order.
- Fleet profile edits/deactivation are not propagated by the existing profile event
  contract (ADR 0008). That existing cross-service gap, and cancellation/reassignment
  of held assignments, are outside this change. UC4/FR9 remains separate.
- Existing databases need a reviewed migration/backfill for new load versions and
  acknowledgement flags. Historical completed assignments must not become permanent
  pending reservations. Use fresh, explicitly chosen demo databases or reconcile
  historical state before testing; no existing data is deleted by this change.

Architecture details for review: [ADR 0010](decisions/0010-dispatch-capacity-reservations-and-zones.md).
