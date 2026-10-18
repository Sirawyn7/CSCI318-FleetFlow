# FleetFlow

An AI-powered, cloud-native delivery and logistics tracking platform built as a
Spring Boot microservices system for CSCI318. See [`docs/AGENTS.md`](docs/AGENTS.md) for
the full architecture and hard constraints, and
[`docs/requirements-outline.md`](docs/requirements-outline.md) for the requirements.

## Module map

| Module | Type | Port | Responsibility |
|---|---|---|---|
| `order-service` | Service | 8081 | Order lifecycle, cancellation (FR1-FR3) |
| `fleet-service` | Service | 8082 | Courier profiles, availability, capacity, location (FR4-FR6, FR10) |
| `dispatch-service` | Service | 8083 | Courier assignment (agentic + rule-based), reassignment (FR7-FR9) |
| `tracking-service` | Service | 8084 | Location stream processing, ETA, geofencing, risk detection (FR11-FR14) |
| `notification-service` | Service | 8085 | Templated alerts, conversational status explainer (FR15-FR16) |
| `gps-simulator` | External client | — | Posts simulated location pings to Fleet's REST endpoint (FR10) |
| `fleetflow-contracts` | Contract artifact | — | Shared event records for the 16-event §6 catalogue |
| `Kafka` | Broker | 9092 | Event backbone. Every service has explicit event bindings; Tracking additionally uses the Kafka Streams binder for its ETA, geofence and risk topologies |

## Structural exceptions

Every service uses the same four-layer package structure
(`api/ → application/ → domain/ → infrastructure/`, see `docs/AGENTS.md`). Two modules
deliberately do not follow it:

- **`gps-simulator`** — it is an external REST client, not a bounded-context service: it
  owns no aggregate, exposes no REST API of its own, and has no direct Kafka access
  (per the locked design decision in `docs/AGENTS.md`). A flat `com.fleetflow.simulator`
  package is the honest structure for that role.
- **`fleetflow-contracts`** — it is a contract artifact shared by the five services, not
  a service itself, so it has no layers to have. See `docs/decisions/0004-fleetflow-contracts-published-language.md`
  for why this is a Published Language and not a Shared Kernel.

## Quick demo (Docker)

The only requirement is Docker Desktop (with the WSL 2 backend on Windows); Java and
Maven are not needed. From the repository root:

```
docker compose up -d --build     # build and start Kafka, the five services, Kafka UI and Dozzle
docker compose run --rm demo     # run the full demo collection, step by step, with pass/fail results
docker compose down -v           # stop everything and reset all data
```

The first build downloads every dependency and takes a few minutes; later builds are
cached. `up` returns once the containers have started; `docker compose ps` shows each
service as `healthy` when it is ready (about a minute). `run --rm demo` also waits for
that before it starts.

While the demo runs, open:

| What | Where |
|---|---|
| Kafka UI: every `fleetflow.*` topic and the events flowing through it | http://localhost:8080 |
| Dozzle: live logs from all containers side by side | http://localhost:9999 |
| HTML report of the last demo run (written when it finishes) | `demo/reports/fleetflow-demo.html` |
| Service APIs, same ports as a local run | http://localhost:8081 – 8085 |

The demo runs the [Postman collection](#demo-postman) with Newman and takes about two
minutes. You can run it again straight away; folder 11 tidies up after each run. Always
reset with `down -v` before a fresh presentation: a plain `down` leaves Kafka's data
volume behind, and rebuilding while running wipes the services' databases but keeps
Kafka's topics and consumer offsets.

To enable the Gemini-backed dispatch agent and status explainer, copy `.env.example` to
`.env` and set `GEMINI_API_KEY` before `up`. Without it, both use their rule-based
fallbacks and the demo still passes.

To work on one service from your IDE against the Compose stack, stop its container
(`docker compose stop order-service`) and run it with `KAFKA_BROKERS=localhost:9094`.
The containerised Notification service still calls Order and Tracking by their
container names.

## Prerequisites

To build and run services outside Docker:

- **JDK 21** (Temurin recommended)
- **Apache Maven 3.9+**

Verify your toolchain:

```
java -version
mvn -version
```

## Build and run

Dispatch accepts optional `GEMINI_API_KEY` and `GEMINI_MODEL` environment variables;
FR7's rule-based path works without a key. Configure `dispatch.zones` in
`dispatch-service/src/main/resources/application.yml`.

Build the whole reactor:

```
mvn clean install
```

Run a single service (each service is independently runnable and owns its own H2
database file under `<service>/data/`):

```
mvn -pl order-service spring-boot:run
```

or run its packaged jar after `mvn install`:

```
java -jar order-service/target/order-service-0.1.0-SNAPSHOT.jar
```

Kafka is a mandated dependency of the project, and every service needs a broker on
`localhost:9092` (override with the `KAFKA_BROKERS` environment variable). Each
service's bindings are listed in its own `application.yml`; the event contracts and
publishing pattern are described in `docs/decisions/0004-fleetflow-contracts-published-language.md`.

To run only the broker from the Compose file and the services from Maven or your IDE:

```
docker compose up -d kafka
```

and set `KAFKA_BROKERS=localhost:9094` for each service.

## Demo (Postman)

[`demo/fleetflow-demo.postman_collection.json`](demo/fleetflow-demo.postman_collection.json)
walks through every use case against the running system. `docker compose run --rm demo`
runs it end to end (see [Quick demo](#quick-demo-docker)). To step through it by hand
instead, start the stack, import the collection into Postman, then run the folders in
order, either one request at a time or with the Collection Runner. Each request's tests
show what the step demonstrates.

| Folder | Shows |
|---|---|
| 1–5 | Courier setup, order placement and assignment, live ETA and geofencing, customer inbox and explainer, proof of delivery |
| 6 | An SLA risk raised on the first ping, the single reassignment attempt finding no replacement, and escalation to the operator queue (re-send 6.8 after 10 minutes) |
| 7 | Reassignment when a courier goes offline mid-delivery |
| 8–10 | Cancellation rules, courier deactivation, and assignment failure |
| 11 | Takes this run's couriers off shift so the collection can be re-run |

Requests marked "(waits)" pause for the `settleMs` collection variable (default
6000 ms) so events can flow between services; raise it on a slow machine. Without
`GEMINI_API_KEY`, couriers are assigned by the rule-based path and the explainer gives
its factual fallback answer; the collection passes either way. For a clean
presentation, start from empty databases (`docker compose down -v`), or make sure no
couriers from earlier sessions are still on shift.

## UC1 — Manage Delivery Order (FR1-FR3)

Order runs on port 8081.

| Method | Path | Purpose |
|---|---|---|
| POST | `/orders` | Create an order and publish `OrderPlaced` |
| GET | `/orders/{id}` | Read status, status history and items |
| POST | `/orders/{id}/cancel` | Cancel a placed order and publish `OrderCancelled` |

Missing orders return 404, invalid input returns 400, and cancellation outside
`PLACED` returns 409. A repeated cancellation does not publish another event.

Order consumes lifecycle events in consumer group `order-service` and records each
transition in the status history, timestamped with the source event's time:

| Event | Status |
|---|---|
| `OrderAssigned` | `ASSIGNED` |
| `DeliveryStarted` | `IN_TRANSIT` |
| `DeliveryCompleted` | `DELIVERED` |
| `AssignmentFailed` | `FAILED` (only from `PLACED`) |

Status only moves forward, so duplicate or late events change nothing, and
`DELIVERED`, `FAILED` and `CANCELLED` are final. Once an order is `ASSIGNED`,
cancellation returns 409. A cancellation accepted while the assignment is still in
flight keeps the order `CANCELLED`; Dispatch then cancels the assignment, Fleet
releases the courier's load, and Tracking stops monitoring the delivery (or never
starts it, if the cancellation arrives first), so no further delivery notifications
follow.
Rejected lifecycle events retry, then go to `error.<source-topic>.order-service`.

Items are modeled as a description (1–255 characters) and a positive quantity, e.g.
`"items":[{"description":"Book","quantity":2}]` in the creation body. If omitted,
`packageDetails` becomes a single item of quantity 1; `packageDetails` itself remains
the overall package summary either way. Orders created before this slice may have an
empty item list, since their quantities were never recorded.

See [ADR 0006](docs/decisions/0006-order-status-history-persistence.md) for the
status-history design, and [ADR 0007](docs/decisions/0007-order-items-and-write-consistency.md)
for item modeling and the flush-before-publish/cancellation-lock behaviour behind the
status codes above — including why this does not guarantee exactly-once creation or
retry idempotency.

Run Order and its contract build:

```text
mvn -pl order-service -am verify
```

For the real Kafka test, start Docker Desktop (using its WSL 2 backend on Windows),
then run:

```text
docker info
wsl --list --verbose
mvn -pl order-service -am test -Dkafka.integration=true
```

Testcontainers starts an isolated `confluentinc/cp-kafka:7.8.0` broker on a random port
and removes it after the test; the normal suite skips it. No separate Kafka
installation inside a WSL distribution is required. Do not delete the existing H2
file to test these changes; tests use isolated in-memory databases.

## UC2 — Manage Fleet and Courier Profiles (FR4-FR6)

Fleet runs on port 8082.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/couriers` | Register and publish `CourierRegistered` |
| GET | `/api/couriers` | List profiles, including deactivated couriers |
| GET | `/api/couriers/{id}` | View profile and active-order count |
| PUT | `/api/couriers/{id}` | Replace vehicle type, capacity and operating zone |
| POST | `/api/couriers/{id}/deactivate` | Deactivate a courier with no active orders |
| PATCH | `/api/couriers/{id}/availability` | Go on/off shift and publish `CourierAvailabilityChanged` |

Registration and update accept, for example:

```json
{"vehicleType":"VAN","capacity":3,"operatingZone":"ZONE_A"}
```

A newly registered courier is not available for dispatch until it goes on shift with
`{"available":true}` on the availability endpoint (`false` takes it off shift).

GET, PUT and deactivation responses include `activeOrderCount` and `deactivated`.
Invalid profile values return 400; missing couriers return 404; capacity and
deactivation conflicts return 409. Deactivation is idempotent. It also takes an
on-shift courier off shift, and a deactivated courier cannot go back on shift (409).
Capacity cannot be reduced below current load.

Fleet consumes `fleetflow.dispatch.order-assigned` and
`fleetflow.tracking.delivery-completed` in consumer group `fleet-service`.
The load counter, order ledger and processed-event ledger commit together.
Duplicate events do not double-count; completed orders stay completed even if their
assignment arrives later. Rejected messages retry then go to the binder's dead-letter
topics (`error.<source-topic>.fleet-service`) for investigation/replay.

**Integration boundaries:** FR6 protects Fleet's local capacity invariant; consuming
an already-published assignment cannot undo Dispatch's decision. Dispatch still
needs its own assignment capacity checks. The event catalogue has no deactivation
event, so deactivating an on-shift courier publishes `CourierAvailabilityChanged`
(`available: false`) instead, which removes the courier from Dispatch's candidates.
Profile updates still persist in Fleet only, because there is no profile-update event.
Cancellation load adjustments are now handled by Fleet's `OrderCancelled` consumer.
Reassignment load transfers are handled by Fleet's `OrderReassigned` consumer.

Run all Fleet tests (requires Docker for the real Kafka test):

```text
mvn -pl fleet-service -am test
```

Run unit, API and H2 persistence tests without Docker:

```text
mvn -pl fleet-service -am test "-Dtest=*Test,!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false
```

## UC3 — Assign Courier to Order (FR7, FR8)

Dispatch runs on port 8083. UC3 is event-driven; it currently has no REST endpoint of
its own. UC4 implements reassignment on exception and publishes `OrderReassigned`;
Notification consumes it to notify the customer. Cancellation releases Dispatch
reservations and Fleet load and prevents further automated reassignment.

- **FR8** (primary path): on `OrderPlaced`, a LangChain4j agent reasons over the open
  orders backlog and courier availability/load, returning a candidate assignment plus
  justification within a 5-second deadline.
- **FR7** (fallback): on agent timeout, error or invalid output, a deterministic rule
  selects the nearest eligible courier by zone, capacity and proximity. An agent
  recommendation is also rechecked for eligibility before it is committed.

Every decision — agent or rule-based — is recorded in `assignment_decisions` with the
path used. Dispatch never calls Fleet synchronously: it maintains its own
`courier_availability_view`, kept current by consuming `CourierRegistered`,
`CourierAvailabilityChanged`, `CourierLocationUpdated` and `CourierLoadChanged`.
Rejected messages retry then go to the binder's dead-letter topics
(`error.<source-topic>.dispatch-service`) for investigation/replay.

See [ADR 0001](docs/decisions/0001-dispatch-open-orders-backlog.md) for the
open-orders backlog design, [ADR 0009](docs/decisions/0009-fleet-publishes-courier-load-changed.md)
for why Dispatch consumes Fleet's load events instead of only the registered ceiling,
and [ADR 0012](docs/decisions/0012-dispatch-capacity-reservations-and-zones.md) for the
agent/rule boundary, zone ownership and capacity reservation design.

Run all Dispatch tests (requires Docker for the real Kafka tests):

```text
mvn -pl dispatch-service -am test
```

Run unit/regression tests without Docker:

```text
mvn -pl dispatch-service -am test "-Dtest=*Test,!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false
```

## UC4 — Handle Delivery Exception (FR9)

An offline courier or predicted SLA breach triggers one automatic reassignment
attempt per active delivery, with a five-second agent limit and rule fallback.
The attempt counts even when no replacement is eligible. Unresolved or subsequent
exceptions enter the operator queue after a 10-minute cooldown; there is no second
automatic attempt. Arrival or completion clears the queue entry. Reassignment
preserves the delivery ID and history and transfers courier load and monitoring.

```text
GET http://localhost:8083/api/dispatch/exceptions
```

The queue returns order, delivery and courier IDs, reason, attempt time and escalation
time. Manual reassignment is outside this slice. See
[ADR 0017](docs/decisions/0017-uc4-durable-reassignment-guard.md) for design details
and limitations.

```text
# Unit, API and persistence regression tests without Docker
mvn -pl dispatch-service,fleet-service,tracking-service -am test "-Dtest=*Test,!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false
# Real Kafka reassignment test; requires Docker
mvn -pl dispatch-service -am test -Dtest=DeliveryExceptionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

## UC5 — Capture Courier Location (FR10)

The GPS simulator posts location pings to Fleet; it has no direct Kafka access.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/couriers/{id}/location` | Record the courier's latest position and publish `CourierLocationUpdated` |

The body is `{"latitude":-33.8688,"longitude":151.2093}`. Fleet stores only the latest
position per courier, not a ping history. `CourierLocationUpdated` is keyed by
courier ID so Tracking's stream-table joins are co-partitioned (see
[ADR 0010](docs/decisions/0010-fr11-internal-topic-and-rekeying.md)).

The simulator reports for one existing courier and does not register one itself.
Register a courier first, then run the simulator with its ID (it pings every 5
seconds by default):

```text
mvn -pl gps-simulator spring-boot:run "-Dspring-boot.run.arguments=--simulator.courier-id=<registered-courier-uuid>"
```

## UC6 — Monitor Delivery in Real Time (FR11, FR12, FR13)

Tracking runs on port 8084. Delivery completion/proof is covered by UC7/FR14 below.

- **FR11:** GPS capture time plus Haversine distance / configured vehicle speed gives
  ETA. Publish the first estimate, then changes greater than 60 seconds. Ignore stale
  or duplicate timestamps. Speeds are configurable under `tracking.eta.speed-kmh`.
- **FR12:** publish near-destination at 500 m and arrival at 50 m, once per delivery.
  The two topics have no cross-topic ordering guarantee (ADR 0011).
- **FR13:** rolling event-time GPS observations detect `STATIONARY`, `OFF_PACE` and
  `SLA_BREACH`; publish `DeliveryAtRisk` with delivery/order/courier IDs. Thresholds
  live under `tracking.risk`; a repeated reason has a 10-minute cooldown per courier,
  so after a reassignment the new courier is evaluated afresh.

`GET /deliveries/order/{orderId}/status` reports the latest ETA and current risk flags
for an order's delivery. A `null` `estimatedArrival` means no ETA yet. `riskFlags`
lists the FR13 reasons (`STATIONARY`, `OFF_PACE`, `SLA_BREACH`) raised for the current
courier within `tracking.risk.alert-cooldown` + `tracking.risk.maximum-ping-gap`
(12 minutes by default): a reason that still holds is re-raised within that time, so
an older one is treated as cleared. `[]` means no current risk, including once the
delivery has arrived, completed or been cancelled. An order with no delivery returns
404 with an error message. See
[ADR 0016](docs/decisions/0016-tracking-status-read-model.md) for why this is a
separate read model rather than querying the Streams topology's state store directly.

Tracking bootstraps deliveries from `OrderPlaced`, `CourierRegistered` and
`OrderAssigned`. Each courier-keyed snapshot contains all deliveries that have not
arrived, completed or been cancelled. On `OrderCancelled`, Tracking marks the delivery
cancelled and republishes the courier's snapshot, which stops ETA, geofence and risk
processing for it once the topologies' tables catch up. A cancellation that arrives
before `OrderAssigned` is remembered, so that delivery is never started. An event
emitted before the tables catch up still never reaches the customer: Notification
checks the order's status before sending and suppresses messages for cancelled orders.
Missing prerequisites cause retries, then the configured DLQ; replay failed assignments
once their prerequisites exist. GPS arriving before the snapshot is materialized does
not join; the next ping updates tracking.

The internal topic is now
`fleetflow.tracking.internal.active-deliveries-by-courier-v2`; it is not a public event.
Startup republishes existing delivery snapshots from Tracking's database. Do not delete
local H2 data to migrate. New v2 stream application IDs isolate the changed schema;
previously published milestones can be emitted again during this upgrade. Old rows
without promised time have no SLA prediction until populated.

Kafka Streams uses `exactly_once_v2` for its state/output transactions. Consumers
requiring committed events must use `isolation.level=read_committed`. JPA bootstrap
and Kafka publication remain separate transactions. Broker transaction support is
required; a single-node development broker must set its transaction-state replication
factor and minimum ISR to 1. Integration tests use an isolated Testcontainers broker.

Run all Tracking tests (Docker required):

```text
mvn -pl tracking-service -am test
```

Run unit/regression tests without Docker:

```text
mvn -pl tracking-service -am test "-Dtest=*Test,!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false
```

See [ADR 0013](docs/decisions/0013-uc6-windowed-risk-and-delivery-snapshots.md) and
[the meaningful prompt record](docs/prompts/0010-uc6-completion-and-review.md).

## UC7 — Complete Delivery and Record Proof (FR14)

UC7 contains one functional requirement: FR14. Tracking stores the courier's route
summary and textual receipt confirmation on the Delivery aggregate, alongside the
existing start timestamp and a server-generated completion timestamp. The summary
is courier supplied; it is not reconstructed from GPS points. No photo upload or
signature storage is required for this slice.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/deliveries/{id}/complete` | Record completion and publish `DeliveryCompleted` |
| GET | `/api/deliveries/{id}` | Read status, timestamps and proof |
| GET | `/api/deliveries?orderId={orderId}` | Read delivery history for an order, oldest first |

Find the delivery ID through the order history endpoint after assignment. Complete
it on port 8084 using the assigned courier ID:

```json
{
  "courierId": "<assigned-courier-uuid>",
  "routeSummary": "Depot via Crown Street to customer drop-off",
  "confirmation": "Received by Alex at reception"
}
```

Text fields must contain 1–255 characters after trimming. Completion returns 200
with `DELIVERED`, both timestamps and proof. Identical retries preserve the original
proof and do not republish. Different proof, a mismatched courier or a cancelled
delivery (reported with status `CANCELLED`) returns 409;
invalid input returns 400; a missing delivery returns 404. An unknown order's history
is an empty list. The supplied courier ID is checked against the assignment, but
this API does not authenticate a caller's identity.

Completion locks the courier projection and delivery row, so concurrent retries
cannot both publish and other snapshot writers cannot restore completed deliveries.
The internal snapshot is published before the external lifecycle event in both
bootstrap and completion. Snapshot failure rolls back without emitting that event.
Kafka and H2 are not an atomic transaction: a database commit failure after a
successful send can still leave an event published. An external-send failure after
the snapshot succeeds can temporarily remove a delivery from monitoring until retry
or recovery repairs it.
Completed deliveries are removed from FR13's courier-keyed list, preserving other
active orders. ETA, geofence and risk joins see the change once their tables catch up;
startup recovery also excludes completed deliveries. See
[ADR 0014](docs/decisions/0014-FR14-completion-proof.md).

Run Tracking tests without Docker:

```text
mvn -pl tracking-service -am test "-Dtest=!Fr*EndToEndIntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Run the FR14 real Kafka integration test with Docker running:

```text
mvn -pl tracking-service -am test -Dtest=Fr14EndToEndIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Existing rows remain in transit with null proof until completed. Schema updates add
nullable columns; do not delete an existing Tracking database.

## UC8 — Notify and Explain Delivery Status (FR15, FR16)

Notification runs on port 8085. See [the UC8 guide](notification-service/README.md)
for its inbox and conversational APIs and tests.
