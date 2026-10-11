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

## Prerequisites

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

Kafka is a mandated dependency of the project, and every service needs a broker on
`localhost:9092` (override with the `KAFKA_BROKERS` environment variable). Each
service's bindings are listed in its own `application.yml`; the event contracts and
publishing pattern are described in `docs/decisions/0004-fleetflow-contracts-published-language.md`.
There's no committed Docker Compose setup yet.

For local development or a manual demo, run a single-node broker yourself, e.g.:

```
docker run -p 9092:9092 apache/kafka:latest
```

(Confirm the current tag on Docker Hub — pin an exact version rather than `latest` for
anything beyond a one-off local demo.)

## UC1 — Manage Delivery Order (FR1-FR3)

Order runs on port 8081.

| Method | Path | Purpose |
|---|---|---|
| POST | `/orders` | Create an order and publish `OrderPlaced` |
| GET | `/orders/{id}` | Read status, status history and items |
| POST | `/orders/{id}/cancel` | Cancel a placed order and publish `OrderCancelled` |

Missing orders return 404, invalid input returns 400, and cancellation outside
`PLACED` returns 409. A repeated cancellation does not publish another event.

Order does not yet consume Dispatch or Tracking events, so an order's status stays
`PLACED` (or `CANCELLED`) for its whole life, and its history records only those
transitions. As a result an order can still be cancelled after a courier is
assigned. Cancellation now marks the Dispatch assignment cancelled, releases its
pending reservation, and releases Fleet's active load through independent
`OrderCancelled` consumers. Duplicate and late assignment events cannot restore
cancelled Fleet load. Tracking cancellation and Order lifecycle status updates
remain outstanding; the delivery being tracked is not stopped by this change.

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
deactivation conflicts return 409. Deactivation is idempotent and separate from
on/off-shift status. Capacity cannot be reduced below current load.

Fleet consumes `fleetflow.dispatch.order-assigned` and
`fleetflow.tracking.delivery-completed` in consumer group `fleet-service`.
The load counter, order ledger and processed-event ledger commit together.
Duplicate events do not double-count; completed orders stay completed even if their
assignment arrives later. Rejected messages retry then go to the binder's dead-letter
topics (`error.<source-topic>.fleet-service`) for investigation/replay.

**Integration boundaries:** FR6 protects Fleet's local capacity invariant; consuming
an already-published assignment cannot undo Dispatch's decision. Dispatch still
needs its own assignment capacity checks. Profile updates and deactivation currently
persist in Fleet only: the existing event catalogue has no profile-update or
deactivation event, so these changes are not propagated to Dispatch's view in this
slice. The availability endpoint does not currently check the deactivation flag, so
a deactivated courier can still be put back on shift and become visible to Dispatch.
for its inbox and conversational APIs and tests. Tracking's `GET
/deliveries/order/{orderId}/status` (UC6 above) now matches the contract Notification
expects; the UC8 guide's own description of it as "proposed, not implemented" needs a
pass to catch up with that merge.


## UC4 — Handle Delivery Exception (FR9)

Dispatch consumes `DeliveryStarted` to identify active deliveries, and closes its
exception handling on `DeliveryArrived` or `DeliveryCompleted`. A current courier
going offline or a `DeliveryAtRisk` with reason `SLA_BREACH` triggers one automatic
reassignment attempt. Other anomaly reasons and risks from the previous courier
are ignored. Missing lifecycle prerequisites retry through the binder and then
require DLQ replay.

The bounded agent remains primary, with the existing five-second limit and rule
fallback. Candidates must have capacity, location and a zone covering the current
courier's last known position (the handoff point). Dispatch reserves capacity under
locks and stores the path and justification, including reassignment fields on the
original `assignment_decisions` audit row. Historical assignments without saved
destination details use rules.

**Locked guard:** one attempt per delivery, including an attempt with no eligible
replacement. Further exceptions are persisted during a 10-minute cooldown measured
from that attempt. At the boundary, unresolved exceptions enter the durable operator
queue; there is no second automatic reassignment. A successful reassignment without
further exceptions does not escalate. Arrival/completion clears the live queue entry.

```text
GET http://localhost:8083/api/dispatch/exceptions
```

The queue returns order, delivery and courier IDs, reason, attempt time and escalation
time. It is a read endpoint for operator triage; manual reassignment is outside FR9.
A scheduled sweep (`dispatch.exceptions.sweep-ms`, default 1000 ms) recovers pending
publications, detects offline couriers and escalates due exceptions after restarts.
Processing can occur after the cooldown boundary when the worker is busy or stopped.

`OrderReassigned` is published from durable assignment state using a stable event ID.
Publication failure leaves it pending; publication retries cannot consume another
reassignment. Fleet transfers the order's load between couriers, and Tracking moves
the existing delivery between courier snapshots, preserving its ID, history and
promised time. Notification does not yet consume `OrderReassigned`.

See [ADR 0017](docs/decisions/0017-uc4-durable-reassignment-guard.md) for implementation
choices, cross-service race handling and remaining eventual-consistency limitations.

```text
# Unit, API and persistence regression tests without Docker
mvn -pl dispatch-service,fleet-service,tracking-service -am test "-Dtest=*Test,!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false
# Real Kafka reassignment test; requires Docker
mvn -pl dispatch-service -am test -Dtest=DeliveryExceptionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```
